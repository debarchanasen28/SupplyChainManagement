package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FLOW A — System2 Procurement → System1 Vendor.
 *
 * After the System1 Vendor confirms a BUYER_APPROVED order (status CONFIRMED), drives the shipping
 * lifecycle one step every 30s: CONFIRMED → PROCESSING → IN_TRANSIT → DELIVERED.
 *
 * STRICT Flow-A filter: systemId = SYSTEM1, direction = OUTBOUND, sourceSystem = system2,
 * targetSystem = system1. Never advances Flow B (INBOUND) orders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class System1VendorShippingLifecycleScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void advanceShipping() {
        List<Order> candidates = orderRepository.findBySystemIdAndDirectionAndStatusIn(
                SystemId.SYSTEM1,
                Direction.OUTBOUND,
                List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT));

        for (Order order : candidates) {
            if (!isFlowA(order)) continue;   // strict source/target guard

            switch (order.getStatus()) {
                case CONFIRMED -> {
                    log.info("Flow A shipping CONFIRMED → PROCESSING orderId={} correlationId={}",
                            order.getOrderId(), order.getCorrelationId());
                    orderService.advanceToProcessing(order);
                }
                case PROCESSING -> {
                    log.info("Flow A shipping PROCESSING → IN_TRANSIT orderId={} correlationId={}",
                            order.getOrderId(), order.getCorrelationId());
                    orderService.advanceToInTransit(order);
                }
                case IN_TRANSIT -> {
                    log.info("Flow A shipping IN_TRANSIT → DELIVERED orderId={} correlationId={}",
                            order.getOrderId(), order.getCorrelationId());
                    orderService.advanceToDelivered(order);
                }
                default -> { }
            }
        }
    }

    private boolean isFlowA(Order order) {
        return "system2".equalsIgnoreCase(order.getSourceSystem())
                && "system1".equalsIgnoreCase(order.getTargetSystem());
    }
}
