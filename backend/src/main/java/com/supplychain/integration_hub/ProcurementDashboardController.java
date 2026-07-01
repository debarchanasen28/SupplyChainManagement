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
@RequestMapping("/api/dashboard/procurement")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProcurementDashboardController {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final AlertRepository alertRepository;
    private final SupplierRepository supplierRepository;

    @GetMapping
    public ResponseEntity<?> getProcurementStats(Authentication authentication) {
      try {
        SystemId systemId = Tenant.of(authentication);

        // All inbound orders (purchase orders) for THIS tenant
        List<Order> allInbound = orderRepository.findBySystemIdAndDirection(
                systemId, Direction.INBOUND);

        // Stat counts
        long pendingOrders = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.REQUESTED)
            .count();

        // Orders where supplier has notified stock — PROCUREMENT must respond YES/NO
        long awaitingOurResponse = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.STOCK_NOTIFIED)
            .count();

        long confirmed = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.CONFIRMED)
            .count();

        long inTransit = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_TRANSIT
                      || o.getStatus() == OrderStatus.PROCESSING)
            .count();

        long delivered = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .count();

        long cancelled = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.CANCELLED
                      || o.getStatus() == OrderStatus.REJECTED)
            .count();

        long totalActive = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.REQUESTED
                      || o.getStatus() == OrderStatus.STOCK_NOTIFIED
                      || o.getStatus() == OrderStatus.CONFIRMED
                      || o.getStatus() == OrderStatus.PROCESSING
                      || o.getStatus() == OrderStatus.IN_TRANSIT)
            .count();

        // Total spend — sum of delivered inbound orders
        double totalSpend = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
            .sum();

        // Committed spend — active orders not yet delivered
        double committedSpend = allInbound.stream()
            .filter(o -> o.getStatus() != OrderStatus.DELIVERED
                      && o.getStatus() != OrderStatus.CANCELLED
                      && o.getStatus() != OrderStatus.REJECTED)
            .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
            .sum();

        // Inbound shipments
        List<Shipment> inboundShipments = shipmentRepository.findBySystemIdAndDirection(systemId, Direction.INBOUND);
        long shipmentsInTransit = inboundShipments.stream()
            .filter(s -> "IN_TRANSIT".equals(s.getStatus())
                      || "OUT_FOR_DELIVERY".equals(s.getStatus()))
            .count();

        // Supplier directory count
        long supplierCount = supplierRepository.countBySystemId(systemId);

        // Recent purchase orders — last 10
        List<Order> recentOrders = allInbound.stream()
            .filter(o -> o.getCreatedAt() != null)
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .limit(10)
            .toList();

        // Orders requiring action (stock check response pending)
        List<Order> actionRequired = allInbound.stream()
            .filter(o -> o.getStatus() == OrderStatus.STOCK_NOTIFIED
                      && (o.getBuyerResponse() == null || o.getBuyerResponse().isBlank()))
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .toList();

        // Unread alerts for PROCUREMENT
        long unreadAlerts = alertRepository.findBySystemIdAndTargetRole(systemId, "PROCUREMENT").stream()
            .filter(a -> "ACTIVE".equals(a.getStatus()))
            .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders",        allInbound.size());
        stats.put("totalActive",         totalActive);
        stats.put("pendingOrders",       pendingOrders);
        stats.put("awaitingOurResponse", awaitingOurResponse);
        stats.put("confirmed",           confirmed);
        stats.put("inTransit",           inTransit);
        stats.put("delivered",           delivered);
        stats.put("cancelled",           cancelled);
        stats.put("totalSpend",          totalSpend);
        stats.put("committedSpend",      committedSpend);
        stats.put("shipmentsInTransit",  shipmentsInTransit);
        stats.put("supplierCount",       supplierCount);
        stats.put("unreadAlerts",        unreadAlerts);
        stats.put("recentOrders",        recentOrders);
        stats.put("actionRequired",      actionRequired);

        return ResponseEntity.ok(stats);
      } catch (Exception e) {
        log.error("Procurement dashboard failed, returning safe defaults: {}", e.getMessage(), e);
        return ResponseEntity.ok(defaultStats());
      }
    }

    private Map<String, Object> defaultStats() {
        Map<String, Object> stats = new HashMap<>();
        for (String key : new String[] {
                "totalOrders", "totalActive", "pendingOrders", "awaitingOurResponse", "confirmed",
                "inTransit", "delivered", "cancelled", "totalSpend", "committedSpend",
                "shipmentsInTransit", "supplierCount", "unreadAlerts" }) {
            stats.put(key, 0);
        }
        stats.put("recentOrders", List.of());
        stats.put("actionRequired", List.of());
        return stats;
    }
}
