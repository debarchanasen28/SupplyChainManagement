package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final AlertRepository alertRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryRepository inventoryRepository;

    public DashboardStats getStats(SystemId systemId) {
        List<Order> allOrders = orderRepository.findBySystemId(systemId);

        long totalOrders = allOrders.size();
        long pendingOrders = allOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.REQUESTED
                || o.getStatus() == OrderStatus.STOCK_NOTIFIED)
            .count();

        List<Shipment> allShipments = shipmentRepository.findBySystemId(systemId);
        long activeShipments = allShipments.stream()
            .filter(s -> !"DELIVERED".equals(s.getStatus()) && !"CANCELLED".equals(s.getStatus()))
            .count();
        long inTransitShipments = allShipments.stream()
            .filter(s -> "IN_TRANSIT".equals(s.getStatus()))
            .count();

        List<Alert> allAlerts = alertRepository.findBySystemId(systemId);
        long openAlerts = allAlerts.stream()
            .filter(a -> "ACTIVE".equals(a.getStatus()))
            .count();
        long highSeverityAlerts = allAlerts.stream()
            .filter(a -> "ACTIVE".equals(a.getStatus())
                && ("LOW_STOCK".equals(a.getType()) || "ORDER_CANCELLED".equals(a.getType())))
            .count();

        long activeSuppliers = supplierRepository.findBySystemId(systemId).stream()
            .filter(s -> Boolean.TRUE.equals(s.getIsActive())
                || "ACTIVE".equals(s.getIntegrationStatus()))
            .count();

        List<InventoryItem> allInventoryItems = inventoryRepository.findBySystemId(systemId);
        long lowStockItems = allInventoryItems.stream()
            .filter(item -> item.getReorderLevel() != null
                && item.getQuantity() != null
                && item.getQuantity() <= item.getReorderLevel())
            .count();

        List<DashboardStats.DayCount> orderTrend = buildOrderTrend(allOrders);

        List<Order> recentOrders = allOrders.stream()
            .filter(o -> o.getCreatedAt() != null)
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(10)
            .toList();

        List<Alert> recentAlerts = allAlerts.stream()
            .filter(a -> "ACTIVE".equals(a.getStatus()) && a.getCreatedAt() != null)
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(10)
            .toList();

        return DashboardStats.builder()
            .totalOrders(totalOrders)
            .pendingOrders(pendingOrders)
            .activeShipments(activeShipments)
            .inTransitShipments(inTransitShipments)
            .openAlerts(openAlerts)
            .highSeverityAlerts(highSeverityAlerts)
            .activeSuppliers(activeSuppliers)
            .inventoryItems(allInventoryItems.size())
            .lowStockItems(lowStockItems)
            .orderTrend(orderTrend)
            .recentOrders(recentOrders)
            .recentAlerts(recentAlerts)
            .build();
    }

    private List<DashboardStats.DayCount> buildOrderTrend(List<Order> orders) {
        List<DashboardStats.DayCount> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long count = orders.stream()
                .filter(o -> o.getCreatedAt() != null
                    && o.getCreatedAt().toLocalDate().equals(day))
                .count();
            trend.add(new DashboardStats.DayCount(
                day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                count
            ));
        }
        return trend;
    }
}
