package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only System 1 business overview for ADMIN/MANAGER.
 * Aggregates existing order, inventory, alert and shipment data — never mutates anything.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final AlertRepository alertRepository;
    private final ShipmentRepository shipmentRepository;
    private final RecoveryMetrics recoveryMetrics;
    private final System2ProcurementOrderGenerator orderGenerator;

    private static final Set<OrderStatus> REJECTED_STATUSES = Set.of(
        OrderStatus.REJECTED, OrderStatus.BUYER_REJECTED, OrderStatus.VENDOR_REJECTED);

    private static final List<String> ACTIVE_SHIPMENT_STATUSES =
        List.of("IN_TRANSIT", "OUT_FOR_DELIVERY");

    private boolean isActive(OrderStatus s) {
        return s != null
            && s != OrderStatus.DELIVERED
            && s != OrderStatus.CANCELLED
            && !REJECTED_STATUSES.contains(s);
    }

    private int threshold(InventoryItem it) {
        if (it.getThresholdQuantity() != null) return it.getThresholdQuantity();
        if (it.getReorderLevel() != null) return it.getReorderLevel();
        return 0;
    }

    private boolean isLowStock(InventoryItem it) {
        int qty = it.getQuantity() == null ? 0 : it.getQuantity();
        return qty < threshold(it);
    }

    private long totalQuantity(List<Order> orders) {
        long sum = 0;
        for (Order o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItem it : o.getItems()) {
                sum += it.getQuantity() == null ? 0 : it.getQuantity();
            }
        }
        return sum;
    }

    /** Top line-items by total quantity across the given orders. */
    private List<Map<String, Object>> topItems(List<Order> orders, int limit) {
        Map<String, Integer> totals = new HashMap<>();
        for (Order o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItem it : o.getItems()) {
                if (it.getDescription() == null) continue;
                int q = it.getQuantity() == null ? 0 : it.getQuantity();
                totals.merge(it.getDescription(), q, Integer::sum);
            }
        }
        return totals.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("item", e.getKey());
                m.put("quantity", e.getValue());
                return m;
            })
            .collect(Collectors.toList());
    }

    private List<Order> recentFirst(List<Order> orders, int limit) {
        return orders.stream()
            .sorted(Comparator.comparing(
                (Order o) -> o.getCreatedAt() == null ? LocalDateTime.MIN : o.getCreatedAt())
                .reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    public Map<String, Object> getSummary(SystemId sid) {
        List<Order> all = orderRepository.findBySystemId(sid);
        List<Order> procurement = all.stream()
            .filter(o -> o.getDirection() == Direction.INBOUND).collect(Collectors.toList());
        List<Order> vendor = all.stream()
            .filter(o -> o.getDirection() == Direction.OUTBOUND).collect(Collectors.toList());

        long active    = all.stream().filter(o -> isActive(o.getStatus())).count();
        long delivered = all.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long cancelled = all.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        long rejected  = all.stream().filter(o -> REJECTED_STATUSES.contains(o.getStatus())).count();

        List<InventoryItem> inv = inventoryRepository.findBySystemId(sid);
        long totalInvQty   = inv.stream().mapToLong(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
        long lowStockCount = inv.stream().filter(this::isLowStock).count();

        List<Alert> recentAlerts = alertRepository.findBySystemId(sid).stream()
            .sorted(Comparator.comparing(
                (Alert a) -> a.getCreatedAt() == null ? LocalDateTime.MIN : a.getCreatedAt())
                .reversed())
            .limit(8)
            .collect(Collectors.toList());

        long activeShipments = shipmentRepository
            .findBySystemIdAndStatusIn(sid, ACTIVE_SHIPMENT_STATUSES).size();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalProcurementOrders", procurement.size());
        m.put("totalVendorOrders",      vendor.size());
        m.put("activeOrders",           active);
        m.put("deliveredOrders",        delivered);
        m.put("cancelledOrders",        cancelled);
        m.put("rejectedOrders",         rejected);
        m.put("lowStockItemCount",      lowStockCount);
        m.put("totalInventoryItems",    inv.size());
        m.put("totalInventoryQuantity", totalInvQty);
        m.put("topBoughtItems",         topItems(procurement, 5));
        m.put("topSoldItems",           topItems(vendor, 5));
        m.put("recentAlerts",           recentAlerts);
        m.put("recentOrders",           recentFirst(all, 10));
        // Extras backing the admin overview cards.
        m.put("activeShipments",        activeShipments);
        m.put("totalGoodsBought",       totalQuantity(procurement));
        m.put("totalGoodsSold",         totalQuantity(vendor));

        // ── Order-recovery visibility (written by the recovery schedulers) ──
        m.put("stuckOrdersCount",        recoveryMetrics.getStuckOrdersCount());
        m.put("recoveredOrdersLastRun",  recoveryMetrics.getRecoveredOrdersLastRun());
        m.put("vendorStuckOrders",       recoveryMetrics.getVendorStuckOrders());
        m.put("procurementStuckOrders",  recoveryMetrics.getProcurementStuckOrders());
        m.put("lastRecoveryRunAt",       recoveryMetrics.getLastRecoveryRunAt());

        // ── Generator open-PO cap as a health indicator (a throttle, not a failure) ──
        long openPoCount = orderGenerator.currentOpenPoCount();
        long openPoCap = orderGenerator.getMaxOpenPos();
        Map<String, Object> capStatus = new LinkedHashMap<>();
        capStatus.put("openPoCount", openPoCount);
        capStatus.put("cap", openPoCap);
        capStatus.put("capReached", openPoCount >= openPoCap);
        m.put("openPoCapStatus", capStatus);
        return m;
    }

    public Map<String, Object> getOrders(SystemId sid, Pageable pageable) {
        List<Order> all = orderRepository.findBySystemId(sid);
        List<Order> procurement = all.stream()
            .filter(o -> o.getDirection() == Direction.INBOUND).collect(Collectors.toList());
        List<Order> vendor = all.stream()
            .filter(o -> o.getDirection() == Direction.OUTBOUND).collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalProcurementOrders", procurement.size());
        m.put("totalVendorOrders",      vendor.size());
        m.put("procurement",            PaginationSupport.page(procurement, pageable));
        m.put("vendor",                 PaginationSupport.page(vendor, pageable));
        m.put("orders",                 PaginationSupport.page(all, pageable));
        return m;
    }

    public Map<String, Object> getInventoryAnalysis(SystemId sid, Pageable pageable) {
        List<InventoryItem> inv = inventoryRepository.findBySystemId(sid);
        List<Order> all = orderRepository.findBySystemId(sid);
        List<Order> procurement = all.stream()
            .filter(o -> o.getDirection() == Direction.INBOUND).collect(Collectors.toList());
        List<Order> vendor = all.stream()
            .filter(o -> o.getDirection() == Direction.OUTBOUND).collect(Collectors.toList());

        List<InventoryItem> lowStock = inv.stream()
            .filter(this::isLowStock).collect(Collectors.toList());
        long totalInvQty = inv.stream()
            .mapToLong(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();

        // NOTE: the admin dashboard does its own client-side pagination over these lists, so they
        // must be returned as full arrays (not PagedResponse objects). Returning paged objects here
        // previously crashed the dashboard render (`topBought.map is not a function`).
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inventory",              inv);
        m.put("lowStock",               lowStock);
        m.put("lowStockItemCount",      lowStock.size());
        m.put("totalInventoryItems",    inv.size());
        m.put("totalInventoryQuantity", totalInvQty);
        m.put("topBought",              topItems(procurement, 100));
        m.put("topSold",                topItems(vendor, 100));
        m.put("totalGoodsBought",       totalQuantity(procurement));
        m.put("totalGoodsSold",         totalQuantity(vendor));
        return m;
    }
}
