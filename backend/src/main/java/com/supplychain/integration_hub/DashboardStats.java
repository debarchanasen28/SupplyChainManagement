package com.supplychain.integration_hub;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardStats {
    private long totalOrders;
    private long pendingOrders;
    private long activeShipments;
    private long inTransitShipments;
    private long openAlerts;
    private long highSeverityAlerts;
    private long activeSuppliers;
    private long inventoryItems;
    private long lowStockItems;
    private List<DayCount> orderTrend;
    private List<Order> recentOrders;
    private List<Alert> recentAlerts;

    @Data @AllArgsConstructor
    public static class DayCount {
        private String day;
        private long orders;
    }
}
