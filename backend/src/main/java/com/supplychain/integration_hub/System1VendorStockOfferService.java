package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FLOW A — System2 Procurement → System1 Vendor.
 *
 * The System1 Vendor's response to an incoming PO: offer stock (even if inventory is 0) or reject.
 *
 * The vendor decision is sent OUT through SAP CPI iFlow3
 * ({@link CpiClient#sendStockOfferViaIflow3}). CPI iFlow3 forwards it to the backend
 * POST /api/cpi/inbound/stock-offer, which updates the System2 procurement copy. The local
 * System1 Vendor order is flipped to STOCK_NOTIFIED (offer) or VENDOR_REJECTED (reject) so the
 * {@link System2ProcurementDecisionWatcher} (SYSTEM1 / OUTBOUND / STOCK_NOTIFIED) picks it up.
 * This service owns Flow-A vendor responses only — it never touches Flow B or iFlow1/iFlow2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class System1VendorStockOfferService {

    private final OrderRepository orderRepository;
    private final AlertService alertService;
    private final InventoryRepository inventoryRepository;
    private final CpiClient cpiClient;

    /** Offer the auto-computed quantity (min on-hand vs required, summed). May be 0. */
    public Order offerStock(String id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;
        int offered = order.getAvailableQuantity() != null
                ? order.getAvailableQuantity() : computeOfferQuantity(order);
        return markStockNotified(order, offered);
    }

    /** Offer a caller-selected quantity. May be 0 (vendor can still offer with zero inventory). */
    public Order offerStock(String id, int offeredQuantity, String note) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;
        if (offeredQuantity < 0) {
            throw new IllegalStateException("offeredQuantity must be 0 or greater for OFFER");
        }
        return markStockNotified(order, offeredQuantity);
    }

    /** Vendor cannot supply — reject the PO and notify procurement via CPI iFlow3. */
    public Order reject(String id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;
        if (order.getSystemId() != SystemId.SYSTEM1 || order.getDirection() != Direction.OUTBOUND) {
            throw new IllegalStateException("Only a System 1 vendor order can be rejected here");
        }
        log.info("stock-offer received via iFlow3 orderId={} correlationId={} decision=REJECT",
                order.getOrderId(), order.getCorrelationId());

        order.setStatus(OrderStatus.VENDOR_REJECTED);
        order.setPoStatus(PoStatus.REJECTED);
        order.setCancelledBy("SYSTEM1_VENDOR");
        order.setVendorDecision("REJECTED");
        order.setRejectionReason("Rejected by vendor");
        order.setResolvedAt(LocalDateTime.now());
        order.setStatusUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        alertService.createOrderAlert(
                "ORDER_REJECTED",
                "Order " + saved.getOrderId() + " rejected by vendor — cannot supply.",
                saved.getId(), "VENDOR");

        sendViaIflow3(saved, "REJECT", 0, "Vendor cannot supply");
        log.info("order updated after iFlow3 orderId={} correlationId={} status={} decision=REJECT",
                saved.getOrderId(), saved.getCorrelationId(), saved.getStatus());
        return saved;
    }

    private Order markStockNotified(Order order, int offered) {
        int offeredQty = Math.max(offered, 0);
        log.info("stock-offer received via iFlow3 orderId={} correlationId={} decision=OFFER offered={}",
                order.getOrderId(), order.getCorrelationId(), offeredQty);
        order.setAvailableQuantity(offeredQty);
        order.setStockCheckSent(true);
        order.setStatus(OrderStatus.STOCK_NOTIFIED);
        order.setStatusUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        alertService.createOrderAlert(
                "STOCK_NOTIFIED",
                "Stock offer recorded for order " + saved.getOrderId() + ": "
                        + saved.getAvailableQuantity() + " units. Awaiting buyer decision.",
                saved.getId(), "VENDOR");

        sendViaIflow3(saved, "OFFER", saved.getAvailableQuantity(), "Vendor stock offer");
        log.info("order updated after iFlow3 orderId={} correlationId={} status={} offered={}",
                saved.getOrderId(), saved.getCorrelationId(), saved.getStatus(), saved.getAvailableQuantity());
        return saved;
    }

    /**
     * Push the vendor decision to System2 Procurement through CPI iFlow3. A CPI failure must not
     * corrupt the local Flow-A order state, so it is logged and swallowed — the order is already
     * STOCK_NOTIFIED / VENDOR_REJECTED and the decision watcher proceeds on the SYSTEM1 side.
     */
    private void sendViaIflow3(Order order, String decision, int offeredQuantity, String note) {
        int required = computeRequiredQuantity(order);
        try {
            cpiClient.sendStockOfferViaIflow3(
                    "system1", "system2",
                    order.getCorrelationId(), order.getOrderId(),
                    decision, Math.max(offeredQuantity, 0), required, note);
        } catch (Exception e) {
            log.warn("CPI iFlow3 stock-offer send failed orderId={} correlationId={} decision={}: {}",
                    order.getOrderId(), order.getCorrelationId(), decision, e.getMessage());
        }
    }

    /** Required quantity = sum of line-item quantities on the order. */
    private int computeRequiredQuantity(Order order) {
        if (order.getItems() == null) return 0;
        int required = 0;
        for (OrderItem it : order.getItems()) {
            required += it.getQuantity() == null ? 0 : it.getQuantity();
        }
        return required;
    }

    /** Auto-computed offer = sum of min(on-hand, required) per line for the order's tenant. */
    private int computeOfferQuantity(Order order) {
        if (order.getItems() == null) return 0;
        SystemId sid = order.getSystemId() != null ? order.getSystemId() : SystemId.SYSTEM1;
        List<InventoryItem> inv = inventoryRepository.findBySystemId(sid);
        int offered = 0;
        for (OrderItem it : order.getItems()) {
            int req = it.getQuantity() == null ? 0 : it.getQuantity();
            int onHand = inv.stream()
                    .filter(x -> x.getItemName() != null && it.getDescription() != null
                              && x.getItemName().trim().equalsIgnoreCase(it.getDescription().trim()))
                    .map(x -> x.getQuantity() == null ? 0 : x.getQuantity())
                    .findFirst().orElse(0);
            offered += Math.min(onHand, req);
        }
        return offered;
    }
}
