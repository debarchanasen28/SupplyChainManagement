package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FLOW A — System2 Procurement → System1 Vendor.
 *
 * Every 30s, decides the System2 Procurement buyer response for System1 Vendor orders the vendor
 * has marked STOCK_NOTIFIED, then sends that decision OUT through SAP CPI iFlow2
 * ({@link CpiClient#sendApproval}). CPI calls back POST /api/cpi/inbound/approval, and the inbound
 * handler ({@link CpiInboundService#receiveApproval} → {@link VendorBuyerApprovalService}) performs
 * the final BUYER_APPROVED / BUYER_REJECTED update on the System1 Vendor order.
 *
 * This watcher never sets the final BUYER_* state directly — that only happens via the iFlow2
 * callback. iFlow3 is not used.
 *
 * STRICT Flow-A filter: systemId = SYSTEM1, direction = OUTBOUND (vendor view),
 * sourceSystem = system2, targetSystem = system1. Never touches Flow B (INBOUND) orders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class System2ProcurementDecisionWatcher {

    private final OrderRepository orderRepository;
    private final System2ProcurementDecisionService decisionService;
    private final CpiClient cpiClient;
    private final CpiAuditService audit;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void processStockNotifiedOrders() {
        List<Order> pending = orderRepository.findBySystemIdAndDirectionAndStatus(
                SystemId.SYSTEM1, Direction.OUTBOUND, OrderStatus.STOCK_NOTIFIED);

        for (Order order : pending) {
            if (!isFlowA(order)) continue;   // strict source/target guard

            // Skip orders whose decision has already been dispatched (poStatus SENT) or whose
            // iFlow2 callback has already applied a final state (APPROVED/REJECTED). Prevents
            // re-deciding / re-sending while the callback is in flight.
            if (order.getPoStatus() == PoStatus.SENT
                    || order.getPoStatus() == PoStatus.APPROVED
                    || order.getPoStatus() == PoStatus.REJECTED) {
                continue;
            }

            if (order.getCorrelationId() == null || order.getCorrelationId().isBlank()) {
                log.warn("Flow A decision skipped — missing correlationId orderId={}", order.getOrderId());
                continue;
            }

            String decision = decisionService.decide(order);            // APPROVED | REJECTED
            boolean approved = decisionService.isApproved(decision);
            String reason = approved
                    ? "System 2 procurement approved stock offer"
                    : "System 2 procurement rejected stock offer";

            // Mark as dispatched BEFORE sending. This write happens before any (possibly
            // synchronous) iFlow2 callback, so the callback's final BUYER_* update is applied
            // AFTER and is never clobbered. status stays STOCK_NOTIFIED until the callback lands.
            order.setPoStatus(PoStatus.SENT);
            order.setStatusUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            // Send the decision through CPI iFlow2. The final BUYER_* update is applied ONLY by the
            // inbound approval callback (VendorBuyerApprovalService) — never written here.
            try {
                String resp = cpiClient.sendApproval(
                        "system2", "system1", order.getCorrelationId(), order.getOrderId(),
                        decision, "SYSTEM2_PROCUREMENT", reason);

                audit.record("OUTBOUND", "APPROVAL", "iFlow2_Approval_Callback",
                        order.getOrderId(), order.getCorrelationId(), "system2", "system1",
                        decision, resp, "SUCCESS", null);
                log.info("Flow A decision dispatched via iFlow2 orderId={} correlationId={} decision={}",
                        order.getOrderId(), order.getCorrelationId(), decision);
                // IMPORTANT: do NOT save 'order' after this point — the callback owns the final state.
            } catch (Exception e) {
                // CPI failure must NOT corrupt order state. Re-read fresh and only revert the
                // dispatch marker if the callback hasn't already applied a final state.
                audit.record("OUTBOUND", "APPROVAL", "iFlow2_Approval_Callback",
                        order.getOrderId(), order.getCorrelationId(), "system2", "system1",
                        decision, null, "FAILED", e.getMessage());
                Order fresh = orderRepository.findById(order.getId()).orElse(null);
                if (fresh != null
                        && fresh.getStatus() == OrderStatus.STOCK_NOTIFIED
                        && fresh.getPoStatus() == PoStatus.SENT) {
                    fresh.setPoStatus(PoStatus.RECEIVED);
                    fresh.setStatusUpdatedAt(LocalDateTime.now());
                    orderRepository.save(fresh);
                }
                log.warn("Flow A iFlow2 send failed orderId={} correlationId={}: {} — will retry",
                        order.getOrderId(), order.getCorrelationId(), e.getMessage());
            }
        }
    }

    private boolean isFlowA(Order order) {
        return "system2".equalsIgnoreCase(order.getSourceSystem())
                && "system1".equalsIgnoreCase(order.getTargetSystem());
    }
}
