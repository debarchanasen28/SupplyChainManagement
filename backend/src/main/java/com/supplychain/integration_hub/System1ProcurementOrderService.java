package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FLOW B — System1 Procurement → System2 Vendor.
 *
 * System1 Procurement manually creates a PO for the System2 Vendor. The PO is dispatched
 * automatically through iFlow1 — there is no "Send to Vendor" step and no buyer approval/rejection.
 * The System2 Vendor decision is applied later by {@link System2VendorDecisionWatcher}.
 *
 * Owns Flow-B order creation ONLY. Produces INBOUND orders with systemId=SYSTEM1,
 * sourceSystem=system1, targetSystem=system2. Never produces BUYER_* states.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class System1ProcurementOrderService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;   // reused for the shared iFlow1 dispatch primitive
    private final InventoryService inventoryService;

    public Order createPurchaseOrder(CreateOrderRequest req, Authentication auth) {
        // Validate + price every line against the System2 Vendor inventory. The frontend price is
        // NEVER trusted: unitPrice and lineTotal are taken from System2 inventory, and totalAmount
        // is recalculated backend-side. This is the source of truth.
        List<OrderItem> items = priceAndValidateAgainstSystem2(req.getItems());
        double total = items.stream()
                .mapToDouble(i -> i.getLineTotal() == null ? 0.0 : i.getLineTotal())
                .sum();

        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
                .orderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .direction(Direction.INBOUND)
                .status(OrderStatus.REQUESTED)
                .sourceSystem("system1")
                .targetSystem("system2")
                .counterpartyId(req.getCounterpartyId())
                .counterpartyName(req.getCounterpartyName())
                .items(items)
                .totalAmount(total)
                .expectedDeliveryDate(req.getExpectedDeliveryDate())
                .notes(req.getNotes())
                .stockCheckSent(false)
                .statusUpdatedAt(now)
                .systemId(Tenant.of(auth))
                .createdAt(now)
                .build();

        Order saved = orderRepository.save(order);
        log.info("System1 Procurement created PO orderId={} — auto-dispatching via iFlow1",
                saved.getOrderId());

        // Auto-dispatch via iFlow1 (stamps correlationId/source/target, flips poStatus to SENT,
        // refreshes the System2 Vendor mirror). No manual send, no buyer approval.
        return orderService.sendOrderToCpi(saved.getId());
    }

    /**
     * Rebuilds the order's line items from the System2 Vendor inventory. For each requested line:
     *   - the SKU must exist in System2 inventory (else 400),
     *   - quantity must be > 0 (else 400),
     *   - unitPrice is taken from System2 inventory (the frontend value is ignored),
     *   - lineTotal = quantity x unitPrice is computed server-side,
     *   - itemName/unit are snapshotted from inventory.
     * Inventory is only READ here — quantities are never modified.
     */
    private List<OrderItem> priceAndValidateAgainstSystem2(List<OrderItem> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("At least one order item is required");
        }
        List<OrderItem> priced = new ArrayList<>();
        for (OrderItem in : requested) {
            String sku = in.getSku();
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("Each item must reference a System2 inventory SKU");
            }
            Integer qty = in.getQuantity();
            if (qty == null || qty <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than 0 for SKU " + sku);
            }
            InventoryItem inv = inventoryService.findSystem2ItemBySku(sku)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "SKU not found in System2 Vendor inventory: " + sku));

            double unitPrice = inv.getUnitPrice() == null ? 0.0 : inv.getUnitPrice();
            double lineTotal = unitPrice * qty;

            priced.add(OrderItem.builder()
                    .sku(inv.getSku())
                    .itemName(inv.getItemName())
                    .description(inv.getItemName())   // keep legacy field populated for existing flows
                    .unit(inv.getUnit() != null ? inv.getUnit() : inv.getUnitOfMeasure())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .totalPrice(lineTotal)            // legacy alias
                    .build());
        }
        return priced;
    }
}
