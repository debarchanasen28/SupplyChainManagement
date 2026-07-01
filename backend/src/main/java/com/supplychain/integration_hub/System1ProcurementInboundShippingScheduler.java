package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FLOW B — System1 Procurement → System2 Vendor.
 *
 * After the System2 Vendor approves a System1 Procurement PO (status CONFIRMED), drives the inbound
 * shipping lifecycle one step every 30s: CONFIRMED → PROCESSING → IN_TRANSIT → DELIVERED.
 *
 * STRICT Flow-B filter: systemId = SYSTEM1, direction = INBOUND, sourceSystem = system1,
 * targetSystem = system2. Never advances Flow A (OUTBOUND vendor) orders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class System1ProcurementInboundShippingScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void advanceShipping() {
        List<Order> candidates = orderRepository.findBySystemIdAndDirectionAndStatusIn(
                SystemId.SYSTEM1,
                Direction.INBOUND,
                List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT));

        for (Order order : candidates) {
            if (!isFlowB(order)) continue;   // strict source/target guard

            switch (order.getStatus()) {
                case CONFIRMED -> {
                    log.info("Flow B shipping CONFIRMED → PROCESSING orderId={} correlationId={}",
                            order.getOrderId(), order.getCorrelationId());
                    orderService.advanceToProcessing(order);
                }
                case PROCESSING -> {
                    log.info("Flow B shipping PROCESSING → IN_TRANSIT orderId={} correlationId={}",
                            order.getOrderId(), order.getCorrelationId());
                    orderService.advanceToInTransit(order);
                }
                case IN_TRANSIT -> {
                    log.info("Flow B shipping IN_TRANSIT → DELIVERED orderId={} correlationId={}",
                            order.getOrderId(), order.getCorrelationId());
                    orderService.advanceToDelivered(order);
                }
                default -> { }
            }
        }
    }

    private boolean isFlowB(Order order) {
        return "system1".equalsIgnoreCase(order.getSourceSystem())
                && "system2".equalsIgnoreCase(order.getTargetSystem());
    }
}
