package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/vendor")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class VendorDashboardController {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final AlertRepository alertRepository;

    @GetMapping
    public ResponseEntity<?> getVendorStats(Authentication authentication) {
      try {
        SystemId systemId = Tenant.of(authentication);

        // All outbound orders for THIS tenant
        List<Order> allOutbound = orderRepository.findBySystemIdAndDirection(
                systemId, Direction.OUTBOUND);

        // Stat counts
        long pendingApprovals = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.REQUESTED)
            .count();

        long awaitingBuyerResponse = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.STOCK_NOTIFIED)
            .count();

        // Buyer approved (via iFlow2) — awaiting the vendor's confirmation.
        long awaitingConfirmation = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.BUYER_APPROVED)
            .count();

        long confirmed = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.CONFIRMED)
            .count();

        long inTransit = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_TRANSIT
                      || o.getStatus() == OrderStatus.PROCESSING)
            .count();

        long delivered = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .count();

        long cancelled = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.CANCELLED
                      || o.getStatus() == OrderStatus.REJECTED
                      || o.getStatus() == OrderStatus.BUYER_REJECTED
                      || o.getStatus() == OrderStatus.VENDOR_REJECTED)
            .count();

        long totalActive = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.REQUESTED
                      || o.getStatus() == OrderStatus.STOCK_NOTIFIED
                      || o.getStatus() == OrderStatus.BUYER_APPROVED
                      || o.getStatus() == OrderStatus.CONFIRMED
                      || o.getStatus() == OrderStatus.PROCESSING
                      || o.getStatus() == OrderStatus.IN_TRANSIT)
            .count();

        // Total revenue from delivered orders
        double totalRevenue = allOutbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
            .sum();

        // Pipeline value (active orders not yet delivered)
        double pipelineValue = allOutbound.stream()
            .filter(o -> o.getStatus() != OrderStatus.DELIVERED
                      && o.getStatus() != OrderStatus.CANCELLED
                      && o.getStatus() != OrderStatus.REJECTED)
            .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
            .sum();

        // Outbound shipments
        List<Shipment> outboundShipments = shipmentRepository.findBySystemIdAndDirection(systemId, Direction.OUTBOUND);
        long shipmentsInTransit = outboundShipments.stream()
            .filter(s -> "IN_TRANSIT".equals(s.getStatus())
                      || "OUT_FOR_DELIVERY".equals(s.getStatus()))
            .count();

        // Recent orders — last 10 by createdAt desc
        List<Order> recentOrders = allOutbound.stream()
            .filter(o -> o.getCreatedAt() != null)
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .limit(10)
            .toList();

        // Unread alerts for VENDOR
        long unreadAlerts = alertRepository.findBySystemIdAndTargetRole(systemId, "VENDOR").stream()
            .filter(a -> "ACTIVE".equals(a.getStatus()))
            .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders",          allOutbound.size());
        stats.put("totalActive",           totalActive);
        stats.put("pendingApprovals",      pendingApprovals);
        stats.put("awaitingBuyerResponse", awaitingBuyerResponse);
        stats.put("awaitingConfirmation",  awaitingConfirmation);
        stats.put("confirmed",             confirmed);
        stats.put("inTransit",             inTransit);
        stats.put("delivered",             delivered);
        stats.put("cancelled",             cancelled);
        stats.put("totalRevenue",          totalRevenue);
        stats.put("pipelineValue",         pipelineValue);
        stats.put("shipmentsInTransit",    shipmentsInTransit);
        stats.put("unreadAlerts",          unreadAlerts);
        stats.put("recentOrders",          recentOrders);

        return ResponseEntity.ok(stats);
      } catch (Exception e) {
        // Never 500 the dashboard — return safe defaults so the UI always renders.
        log.error("Vendor dashboard failed, returning safe defaults: {}", e.getMessage(), e);
        return ResponseEntity.ok(defaultStats());
      }
    }

    private Map<String, Object> defaultStats() {
        Map<String, Object> stats = new HashMap<>();
        for (String key : new String[] {
                "totalOrders", "totalActive", "pendingApprovals", "awaitingBuyerResponse",
                "awaitingConfirmation", "confirmed", "inTransit", "delivered", "cancelled",
                "totalRevenue", "pipelineValue", "shipmentsInTransit", "unreadAlerts" }) {
            stats.put(key, 0);
        }
        stats.put("recentOrders", java.util.List.of());
        return stats;
    }
}
