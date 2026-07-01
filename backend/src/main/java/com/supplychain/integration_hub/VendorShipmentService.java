package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorShipmentService {

    private static final List<OrderStatus> SHIPPING_STATUSES = List.of(
            OrderStatus.VENDOR_CONFIRMED,
            OrderStatus.CONFIRMED,
            OrderStatus.ACTIVE,
            OrderStatus.PROCESSING,
            OrderStatus.IN_TRANSIT,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    public List<VendorShipmentView> getShipments(Authentication authentication) {
        requireSystem1Vendor(authentication);
        return findShippingOrders().stream().map(this::toView).toList();
    }

    public Page<VendorShipmentView> getShipments(
            Authentication authentication, Pageable pageable) {
        requireSystem1Vendor(authentication);
        return orderRepository.findVendorShippingOrders(
                        SystemId.SYSTEM1, Direction.OUTBOUND, SHIPPING_STATUSES, pageable)
                .map(this::toView);
    }

    public List<VendorShipmentView> getActiveShipments(Authentication authentication) {
        requireSystem1Vendor(authentication);
        return findShippingOrders().stream()
                .filter(order -> normalizedStatus(order) != OrderStatus.DELIVERED)
                .map(this::toView)
                .toList();
    }

    public List<VendorShipmentView> getPastShipments(Authentication authentication) {
        requireSystem1Vendor(authentication);
        return findShippingOrders().stream()
                .filter(order -> normalizedStatus(order) == OrderStatus.DELIVERED)
                .map(this::toView)
                .toList();
    }

    public boolean isSystem1Vendor(Authentication authentication) {
        return authentication != null
                && Tenant.of(authentication) == SystemId.SYSTEM1
                && authentication.getAuthorities().stream()
                    .anyMatch(authority -> "VENDOR".equals(authority.getAuthority()));
    }

    private List<Order> findShippingOrders() {
        return orderRepository.findVendorShippingOrders(
                        SystemId.SYSTEM1, Direction.OUTBOUND, SHIPPING_STATUSES).stream()
                .sorted(Comparator.comparing(
                        this::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private VendorShipmentView toView(Order order) {
        OrderStatus status = normalizedStatus(order);
        List<String> itemNames = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(OrderItem::getDescription)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        int quantity = order.getItems() == null ? 0 : order.getItems().stream()
                .map(OrderItem::getQuantity)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .sum();
        String displayStatus = status.name();

        return VendorShipmentView.builder()
                .id(order.getId())
                .shipmentId(order.getOrderId())
                .orderId(order.getOrderId())
                .correlationId(order.getCorrelationId())
                .systemId(order.getSystemId())
                .direction(order.getDirection())
                .itemNames(itemNames)
                .quantity(quantity)
                .buyer(order.getCounterpartyName())
                .customer(order.getCounterpartyName())
                .counterpartyName(order.getCounterpartyName())
                .status(displayStatus)
                .shippingStatus(shippingStatus(status))
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .estimatedDelivery(order.getExpectedDeliveryDate())
                .updatedAt(updatedAt(order))
                .statusUpdatedAt(order.getStatusUpdatedAt())
                .buyerResponse(order.getBuyerResponse())
                .vendorConfirmed(isVendorConfirmed(order))
                .build();
    }

    private OrderStatus normalizedStatus(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED) return OrderStatus.DELIVERED;
        if (order.getStatus() == OrderStatus.SHIPPED || order.getPoStatus() == PoStatus.SHIPPED) {
            return OrderStatus.SHIPPED;
        }
        if (order.getStatus() == OrderStatus.IN_TRANSIT) return OrderStatus.IN_TRANSIT;
        if (order.getStatus() == OrderStatus.PROCESSING) return OrderStatus.PROCESSING;
        return OrderStatus.VENDOR_CONFIRMED;
    }

    private String shippingStatus(OrderStatus status) {
        return switch (status) {
            case VENDOR_CONFIRMED -> "PENDING";
            case PROCESSING -> "PROCESSING";
            case SHIPPED -> "SHIPPED";
            case IN_TRANSIT -> "IN_TRANSIT";
            case DELIVERED -> "DELIVERED";
            default -> status.name();
        };
    }

    private boolean isVendorConfirmed(Order order) {
        return order.getStatus() == OrderStatus.VENDOR_CONFIRMED
                || order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.ACTIVE
                || "CONFIRMED".equalsIgnoreCase(order.getVendorFinalDecision());
    }

    private LocalDateTime updatedAt(Order order) {
        return order.getStatusUpdatedAt() == null ? order.getCreatedAt() : order.getStatusUpdatedAt();
    }

    private void requireSystem1Vendor(Authentication authentication) {
        if (!isSystem1Vendor(authentication)) {
            throw new AccessDeniedException("System1 vendor access is required");
        }
    }
}
