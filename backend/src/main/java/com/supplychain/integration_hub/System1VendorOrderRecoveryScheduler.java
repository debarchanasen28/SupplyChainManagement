package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * FLOW A recovery — System2 Procurement → System1 Vendor.
 *
 * Every 45s, scans System1 Vendor orders that are stuck (no status change for a while) before a
 * final status and nudges them back into their flow. It is a SAFETY NET layered on top of the
 * 30s decision/shipping schedulers: it only touches orders that have been idle past
 * {@link #STUCK_THRESHOLD_SECONDS}, so it never races the primary schedulers.
 *
 * STRICT Flow-A filter: systemId=SYSTEM1, direction=OUTBOUND, sourceSystem=system2,
 * targetSystem=system1. Never touches Flow B (procurement/INBOUND) orders. Idempotent — it
 * re-dispatches/advances existing operations, never creates orders or duplicates final decisions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class System1VendorOrderRecoveryScheduler {

    private static final long STUCK_THRESHOLD_SECONDS = 120;

    // Final statuses are never retried.
    private static final Set<OrderStatus> FINAL_STATUSES = EnumSet.of(
            OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REJECTED,
            OrderStatus.BUYER_REJECTED, OrderStatus.VENDOR_REJECTED);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final IntegrationLogService integrationLogService;
    private final RecoveryMetrics recoveryMetrics;

    @Scheduled(fixedDelay = 45_000, initialDelay = 45_000)
    public void recoverStuckOrders() {
        List<Order> all = orderRepository.findBySystemIdAndDirection(SystemId.SYSTEM1, Direction.OUTBOUND);
        LocalDateTime now = LocalDateTime.now();

        int scanned = 0, stuck = 0, recovered = 0, finalsSkipped = 0;

        for (Order order : all) {
            if (!isFlowA(order)) continue;
            scanned++;

            if (isFinal(order.getStatus())) {
                finalsSkipped++;
                continue;   // RECOVERY_SKIPPED_FINAL (summarized below)
            }
            if (!isStuck(order, now)) continue;   // primary schedulers are still handling it

            stuck++;
            OrderStatus oldStatus = order.getStatus();
            String action = "RECOVERY_NOOP";
            String result = "";

            switch (oldStatus) {
                case STOCK_NOTIFIED -> {
                    // Decision was dispatched (poStatus SENT) but the iFlow2 callback never landed —
                    // clear the marker so System2ProcurementDecisionWatcher re-dispatches via iFlow2.
                    if (order.getPoStatus() == PoStatus.SENT) {
                        order.setPoStatus(PoStatus.RECEIVED);
                        order.setStatusUpdatedAt(now);
                        orderRepository.save(order);
                        recovered++;
                        action = "RECOVERY_REDISPATCH_DECISION";
                        result = "re-dispatch armed (poStatus SENT -> RECEIVED)";
                        logEvent(order, action, result);
                    } else {
                        action = "RECOVERY_REDISPATCH_DECISION";
                        result = "awaiting decision watcher (poStatus=" + order.getPoStatus() + ")";
                    }
                }
                case CONFIRMED, PROCESSING, IN_TRANSIT -> {
                    advanceShipping(order);
                    recovered++;
                    action = "RECOVERY_RESUME_SHIPPING";
                    result = "advanced from " + oldStatus;
                    logEvent(order, action, result);
                }
                case REQUESTED -> {
                    action = "RECOVERY_NOOP";
                    result = "left for vendor action";
                }
                case BUYER_APPROVED -> {
                    action = "RECOVERY_NOOP";
                    result = "awaiting vendor confirmation";
                }
                default -> {
                    action = "RECOVERY_NOOP";
                    result = "no rule for status " + oldStatus;
                }
            }

            // Required per-attempt log line: orderId, correlationId, oldStatus, recoveryAction, result.
            log.info("Flow A recovery orderId={} correlationId={} oldStatus={} action={} result={}",
                    order.getOrderId(), order.getCorrelationId(), oldStatus, action, result);
        }

        if (finalsSkipped > 0) {
            integrationLogService.logIntegrationEvent(null, "system2", "system1",
                    "RECOVERY_SKIPPED_FINAL", "SUCCESS",
                    "Flow A recovery skipped " + finalsSkipped + " final order(s)");
        }
        integrationLogService.logIntegrationEvent(null, "system2", "system1",
                "RECOVERY_SCAN", "SUCCESS",
                "Flow A recovery scan: scanned=" + scanned + " stuck=" + stuck
                        + " recovered=" + recovered + " finalsSkipped=" + finalsSkipped);

        recoveryMetrics.recordVendorRun(stuck, recovered);
        if (stuck > 0 || recovered > 0) {
            log.info("Flow A recovery run complete scanned={} stuck={} recovered={}",
                    scanned, stuck, recovered);
        }
    }

    private void advanceShipping(Order order) {
        switch (order.getStatus()) {
            case CONFIRMED -> orderService.advanceToProcessing(order);
            case PROCESSING -> orderService.advanceToInTransit(order);
            case IN_TRANSIT -> orderService.advanceToDelivered(order);
            default -> { }
        }
    }

    private void logEvent(Order order, String eventType, String result) {
        integrationLogService.logIntegrationEvent(order.getCorrelationId(), "system2", "system1",
                eventType, "SUCCESS",
                "orderId=" + order.getOrderId() + " status=" + order.getStatus() + " — " + result);
    }

    private boolean isStuck(Order order, LocalDateTime now) {
        if (order.getStatusUpdatedAt() == null) return true;
        return Duration.between(order.getStatusUpdatedAt(), now).getSeconds() >= STUCK_THRESHOLD_SECONDS;
    }

    private boolean isFinal(OrderStatus status) {
        return status == null || FINAL_STATUSES.contains(status);
    }

    private boolean isFlowA(Order order) {
        return "system2".equalsIgnoreCase(order.getSourceSystem())
                && "system1".equalsIgnoreCase(order.getTargetSystem());
    }
}
