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
 * FLOW B recovery — System1 Procurement → System2 Vendor.
 *
 * Every 45s, scans System1 Procurement orders stuck before a final status and nudges them back
 * into their flow. SAFETY NET over the 30s decision/inbound-shipping schedulers: it only acts on
 * orders idle past {@link #STUCK_THRESHOLD_SECONDS}.
 *
 * STRICT Flow-B filter: systemId=SYSTEM1, direction=INBOUND, sourceSystem=system1,
 * targetSystem=system2. Never touches Flow A (vendor/OUTBOUND) orders and never applies any
 * BUYER_* logic. Idempotent — re-dispatches/advances, never creates orders or duplicate decisions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class System1ProcurementOrderRecoveryScheduler {

    private static final long STUCK_THRESHOLD_SECONDS = 120;

    private static final Set<OrderStatus> FINAL_STATUSES = EnumSet.of(
            OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REJECTED);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final IntegrationLogService integrationLogService;
    private final RecoveryMetrics recoveryMetrics;

    @Scheduled(fixedDelay = 45_000, initialDelay = 45_000)
    public void recoverStuckOrders() {
        List<Order> all = orderRepository.findBySystemIdAndDirection(SystemId.SYSTEM1, Direction.INBOUND);
        LocalDateTime now = LocalDateTime.now();

        int scanned = 0, stuck = 0, recovered = 0, finalsSkipped = 0;

        for (Order order : all) {
            if (!isFlowB(order)) continue;
            scanned++;

            if (isFinal(order.getStatus())) {
                finalsSkipped++;
                continue;
            }
            if (!isStuck(order, now)) continue;

            stuck++;
            OrderStatus oldStatus = order.getStatus();
            String action = "RECOVERY_NOOP";
            String result = "";

            switch (oldStatus) {
                case REQUESTED -> {
                    // The System2 Vendor decision is owned by System2VendorDecisionWatcher (every 30s).
                    // We do NOT apply the decision here (would risk a duplicate decision); we only flag
                    // the stuck order so it stays visible. A missing correlationId is an iFlow1 concern.
                    action = "RECOVERY_REDISPATCH_DECISION";
                    result = order.getCorrelationId() == null
                            ? "blocked — missing correlationId (iFlow1)"
                            : "awaiting System2 Vendor decision watcher";
                    logEvent(order, action, result);
                }
                case CONFIRMED, PROCESSING, IN_TRANSIT -> {
                    advanceShipping(order);
                    recovered++;
                    action = "RECOVERY_RESUME_SHIPPING";
                    result = "advanced from " + oldStatus;
                    logEvent(order, action, result);
                }
                default -> {
                    action = "RECOVERY_NOOP";
                    result = "no rule for status " + oldStatus;
                }
            }

            log.info("Flow B recovery orderId={} correlationId={} oldStatus={} action={} result={}",
                    order.getOrderId(), order.getCorrelationId(), oldStatus, action, result);
        }

        if (finalsSkipped > 0) {
            integrationLogService.logIntegrationEvent(null, "system1", "system2",
                    "RECOVERY_SKIPPED_FINAL", "SUCCESS",
                    "Flow B recovery skipped " + finalsSkipped + " final order(s)");
        }
        integrationLogService.logIntegrationEvent(null, "system1", "system2",
                "RECOVERY_SCAN", "SUCCESS",
                "Flow B recovery scan: scanned=" + scanned + " stuck=" + stuck
                        + " recovered=" + recovered + " finalsSkipped=" + finalsSkipped);

        recoveryMetrics.recordProcurementRun(stuck, recovered);
        if (stuck > 0 || recovered > 0) {
            log.info("Flow B recovery run complete scanned={} stuck={} recovered={}",
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
        integrationLogService.logIntegrationEvent(order.getCorrelationId(), "system1", "system2",
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

    private boolean isFlowB(Order order) {
        return "system1".equalsIgnoreCase(order.getSourceSystem())
                && "system2".equalsIgnoreCase(order.getTargetSystem());
    }
}
