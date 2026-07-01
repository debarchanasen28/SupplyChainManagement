package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FLOW B — System1 Procurement → System2 Vendor.
 *
 * Every 30s, applies the System2 Vendor's accept/reject decision to System1 Procurement POs that
 * were auto-dispatched via iFlow1 (status REQUESTED with a correlationId). Accepted → CONFIRMED;
 * rejected → REJECTED with reason "Rejected by System2 Vendor".
 *
 * STRICT Flow-B filter: systemId = SYSTEM1, direction = INBOUND (procurement view),
 * sourceSystem = system1, targetSystem = system2. Never touches Flow A (OUTBOUND vendor) orders,
 * and never produces BUYER_* states.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class System2VendorDecisionWatcher {

    private final OrderRepository orderRepository;
    private final System2VendorDecisionService decisionService;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void applyVendorDecisions() {
        List<Order> pending = orderRepository.findBySystemIdAndDirectionAndStatus(
                SystemId.SYSTEM1, Direction.INBOUND, OrderStatus.REQUESTED);

        for (Order order : pending) {
            if (!isFlowB(order)) continue;                 // strict source/target guard
            if (order.getCorrelationId() == null || order.getCorrelationId().isBlank()) continue;

            String decision = decisionService.decide(order);
            boolean accepted = decisionService.isAccepted(decision);

            Order updated = orderService.applySystem2VendorDecision(order, accepted);
            log.info("System2 Vendor {} System1 Procurement PO orderId={} correlationId={} -> {}",
                    accepted ? "accepted" : "rejected",
                    updated.getOrderId(), updated.getCorrelationId(), updated.getStatus());
        }
    }

    private boolean isFlowB(Order order) {
        return "system1".equalsIgnoreCase(order.getSourceSystem())
                && "system2".equalsIgnoreCase(order.getTargetSystem());
    }
}
