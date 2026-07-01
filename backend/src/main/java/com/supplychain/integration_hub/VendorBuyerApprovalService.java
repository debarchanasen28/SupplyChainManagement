package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorBuyerApprovalService {

    private final OrderRepository orderRepository;
    private final AlertService alertService;

    public Order apply(ApprovalCallbackRequest req) {
        return applyDecision(req.getCorrelationId(), req.getDecision());
    }

    public Order applyDecision(String correlationId, String decision) {
        // This buyer-approval lane belongs ONLY to the System2 Procurement → System1 Vendor flow,
        // whose System1-side record is the VENDOR (OUTBOUND) mirror. A System1 PROCUREMENT (INBOUND)
        // order may share this correlationId but belongs to the System1 Procurement → System2 Vendor
        // lane and must never receive BUYER_* states. Returning null for it lets CpiInboundService
        // apply the correct CONFIRMED/REJECTED status instead.
        Order order = orderRepository.findBySystemIdAndDirectionAndCorrelationId(
                SystemId.SYSTEM1, Direction.OUTBOUND, correlationId);
        if (order == null) return null;

        // STRICT Flow A guard — only the System2 Procurement → System1 Vendor mirror:
        // systemId=SYSTEM1, direction=OUTBOUND (VENDOR view), sourceSystem=system2, targetSystem=system1.
        // Never touch the System2 source order or any System1 Procurement (INBOUND) order.
        if (!"system2".equalsIgnoreCase(order.getSourceSystem())
                || !"system1".equalsIgnoreCase(order.getTargetSystem())) {
            return null;
        }
        log.info("System1 Vendor mirror found orderId={} correlationId={} status={}",
                order.getOrderId(), order.getCorrelationId(), order.getStatus());

        boolean approved = "APPROVED".equalsIgnoreCase(decision);
        if (approved
                && "YES".equalsIgnoreCase(order.getBuyerResponse())
                && (order.getStatus() == OrderStatus.BUYER_APPROVED
                    || order.getStatus() == OrderStatus.ACTIVE
                    || order.getStatus() == OrderStatus.IN_TRANSIT
                    || order.getStatus() == OrderStatus.DELIVERED)) {
            log.info("System1 buyer approval callback dedup orderId={} correlationId={} status={}",
                    order.getOrderId(), order.getCorrelationId(), order.getStatus());
            return order;
        }
        if (!approved
                && "NO".equalsIgnoreCase(order.getBuyerResponse())
                && order.getStatus() == OrderStatus.BUYER_REJECTED) {
            log.info("System1 buyer rejection callback dedup orderId={} correlationId={}",
                    order.getOrderId(), order.getCorrelationId());
            return order;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setBuyerResponse(approved ? "YES" : "NO");
        order.setBuyerDecision(approved ? "APPROVED" : "REJECTED");
        order.setBuyerDecisionReason(approved
                ? "Approved by System2 Procurement"
                : "Rejected by System2 Procurement");
        order.setPoStatus(approved ? PoStatus.APPROVED : PoStatus.REJECTED);
        order.setStatus(approved ? OrderStatus.BUYER_APPROVED : OrderStatus.BUYER_REJECTED);
        order.setCancelledBy(approved ? null : "SYSTEM2_PROCUREMENT");
        order.setStatusUpdatedAt(LocalDateTime.now());
        if (!approved) {
            order.setRejectionReason("Rejected by System2 Procurement");
            order.setResolvedAt(LocalDateTime.now());
        }
        Order saved = orderRepository.save(order);

        log.info("System1 Vendor mirror updated {} -> {} orderId={} correlationId={} buyerResponse={}",
                oldStatus, saved.getStatus(), saved.getOrderId(), saved.getCorrelationId(),
                saved.getBuyerResponse());
        try {
            alertService.createOrderAlert(
                    approved ? "STOCK_ACCEPTED" : "ORDER_REJECTED",
                    approved
                            ? "Buyer approved stock offer for PO " + saved.getOrderId()
                                + ". Vendor confirmation is required."
                            : "Buyer rejected stock offer for PO " + saved.getOrderId() + ".",
                    saved.getId(), "VENDOR");
        } catch (Exception e) {
            log.warn("Vendor buyer-decision alert failed correlationId={}: {}",
                    saved.getCorrelationId(), e.getMessage());
        }
        return saved;
    }
}
