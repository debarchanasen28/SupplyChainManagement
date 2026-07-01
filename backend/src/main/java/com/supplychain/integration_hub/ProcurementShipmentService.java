package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the System1 Procurement shipment views from inbound purchase orders.
 *
 * These are orders where: systemId = (tenant, SYSTEM1 by default), direction = INBOUND,
 * sourceSystem = system1, targetSystem = system2, counterparty = System2 Vendor, and the
 * status has advanced to a confirmed/shipping stage. Kept entirely separate from
 * {@link VendorShipmentService} so vendor (OUTBOUND) behaviour is untouched.
 */
@Service
@RequiredArgsConstructor
public class ProcurementShipmentService {

    // "All Shipments" — confirmed + shipping-related inbound orders.
    private static final List<OrderStatus> ALL_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.IN_TRANSIT,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    // "Active" — still in flight.
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.IN_TRANSIT,
            OrderStatus.SHIPPED);

    // "Past" — terminal states.
    private static final List<OrderStatus> PAST_STATUSES = List.of(
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED,
            OrderStatus.REJECTED);

    private final OrderRepository orderRepository;

    public Page<ProcurementShipmentView> getShipments(
            Authentication authentication, Pageable pageable) {
        SystemId systemId = requireProcurement(authentication);
        return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                        systemId, Direction.INBOUND, ALL_STATUSES, pageable)
                .map(this::toView);
    }

    public List<ProcurementShipmentView> getActiveShipments(Authentication authentication) {
        SystemId systemId = requireProcurement(authentication);
        return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                        systemId, Direction.INBOUND, ACTIVE_STATUSES).stream()
                .map(this::toView)
                .toList();
    }

    public List<ProcurementShipmentView> getPastShipments(Authentication authentication) {
        SystemId systemId = requireProcurement(authentication);
        return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                        systemId, Direction.INBOUND, PAST_STATUSES).stream()
                .map(this::toView)
                .toList();
    }

    public boolean isProcurement(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                    .anyMatch(authority -> "PROCUREMENT".equals(authority.getAuthority()));
    }

    private ProcurementShipmentView toView(Order order) {
        List<String> itemNames = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(OrderItem::getDescription)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        int quantity = order.getItems() == null ? 0 : order.getItems().stream()
                .map(OrderItem::getQuantity)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .sum();
        String status = order.getStatus() == null ? "" : order.getStatus().name();

        return ProcurementShipmentView.builder()
                .id(order.getId())
                .orderId(order.getOrderId())
                .correlationId(order.getCorrelationId())
                .systemId(order.getSystemId())
                .direction(order.getDirection())
                .itemNames(itemNames)
                .quantity(quantity)
                .counterpartyName(order.getCounterpartyName())
                .supplier(order.getCounterpartyName())
                .status(status)
                .poStatus(order.getPoStatus())
                .shippingStatus(status)
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .estimatedDelivery(order.getExpectedDeliveryDate())
                .totalAmount(order.getTotalAmount())
                .statusUpdatedAt(order.getStatusUpdatedAt())
                .updatedAt(order.getStatusUpdatedAt() == null
                        ? order.getCreatedAt() : order.getStatusUpdatedAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private SystemId requireProcurement(Authentication authentication) {
        if (!isProcurement(authentication)) {
            throw new AccessDeniedException("Procurement access is required");
        }
        return Tenant.of(authentication);
    }
}
