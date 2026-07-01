package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateAlertId() {
        return "ALT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String getRole(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority();
    }

    private SystemId getSystemId(Authentication auth) {
        return Tenant.of(auth);
    }

    private SystemId inferSystemId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) return SystemId.SYSTEM1;

        Order order = orderRepository.findById(referenceId).orElse(null);
        if (order != null && order.getSystemId() != null) return order.getSystemId();

        Shipment shipment = shipmentRepository.findById(referenceId).orElse(null);
        if (shipment != null && shipment.getSystemId() != null) return shipment.getSystemId();

        InventoryItem item = inventoryRepository.findById(referenceId).orElse(null);
        if (item != null && item.getSystemId() != null) return item.getSystemId();

        return SystemId.SYSTEM1;
    }

    // ── Event-driven alert creation (called by OrderService, ShipmentService) ─

    public Alert createOrderAlert(String type, String message,
                                  String referenceId, String targetRole) {
        String severity = resolveSeverity(type);

        Alert alert = Alert.builder()
            .alertId(generateAlertId())
            .type(type)
            .message(message)
            .severity(severity)
            .status("ACTIVE")
            .targetRole(targetRole != null ? targetRole : "ALL")
            .referenceId(referenceId)
            .systemId(inferSystemId(referenceId))
            .build();

        return alertRepository.save(alert);
    }

    private String resolveSeverity(String type) {
        return switch (type) {
            case "ORDER_CANCELLED", "ORDER_REJECTED", "LOW_STOCK",
                 "SHIPMENT_DELAYED"                   -> "HIGH";
            case "ORDER_RECEIVED", "STOCK_NOTIFIED",
                 "ORDER_CONFIRMED"                    -> "MEDIUM";
            default                                   -> "LOW";
        };
    }

    /** True if there's already an unresolved LOW_STOCK alert for this inventory item (dedup). */
    public boolean hasActiveLowStockAlert(String referenceId) {
        SystemId systemId = inferSystemId(referenceId);
        return alertRepository.findBySystemIdAndStatus(systemId, "ACTIVE").stream()
            .anyMatch(a -> "LOW_STOCK".equals(a.getType()) && referenceId.equals(a.getReferenceId()));
    }

    // ── Read — role-filtered ──────────────────────────────────────────────────

    public List<Alert> getAllAlerts(Authentication auth) {
        String role = getRole(auth);
        SystemId systemId = getSystemId(auth);

        // ADMIN and MANAGER see everything inside their tenant.
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) {
            return alertRepository.findBySystemId(systemId)
                .stream()
                .sorted((a, b) -> {
                    var x = a.getCreatedAt(); var y = b.getCreatedAt();
                    if (x == null && y == null) return 0;
                    if (x == null) return 1;        // nulls last
                    if (y == null) return -1;
                    return y.compareTo(x);          // newest first
                })
                .toList();
        }

        // VENDOR and PROCUREMENT see their own alerts + ALL alerts
        return alertRepository.findBySystemIdAndTargetRoleIn(systemId, List.of(role, "ALL"))
            .stream()
            .sorted((a, b) -> {
                    var x = a.getCreatedAt(); var y = b.getCreatedAt();
                    if (x == null && y == null) return 0;
                    if (x == null) return 1;        // nulls last
                    if (y == null) return -1;
                    return y.compareTo(x);          // newest first
                })
            .toList();
    }

    public Page<Alert> getAllAlerts(Authentication auth, Pageable pageable) {
        String role = getRole(auth);
        SystemId systemId = getSystemId(auth);
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) {
            return alertRepository.findBySystemId(systemId, pageable);
        }
        return alertRepository.findBySystemIdAndTargetRoleIn(
                systemId, List.of(role, "ALL"), pageable);
    }

    public List<Alert> getActiveAlerts(Authentication auth) {
        String role = getRole(auth);
        SystemId systemId = getSystemId(auth);
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) {
            return alertRepository.findBySystemIdAndStatus(systemId, "ACTIVE");
        }
        return alertRepository.findBySystemIdAndTargetRoleInAndStatus(systemId, List.of(role, "ALL"), "ACTIVE");
    }

    public Page<Alert> getActiveAlerts(Authentication auth, Pageable pageable) {
        String role = getRole(auth);
        SystemId systemId = getSystemId(auth);
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) {
            return alertRepository.findBySystemIdAndStatus(systemId, "ACTIVE", pageable);
        }
        return alertRepository.findBySystemIdAndTargetRoleInAndStatus(
                systemId, List.of(role, "ALL"), "ACTIVE", pageable);
    }

    // ── Resolve ───────────────────────────────────────────────────────────────

    public Alert resolveAlert(String id, Authentication auth) {
        Alert alert = alertRepository.findById(id).orElse(null);
        if (alert == null || alert.getSystemId() != getSystemId(auth)) return null;
        alert.setStatus("RESOLVED");
        return alertRepository.save(alert);
    }

    public void resolveAll(Authentication auth) {
        getActiveAlerts(auth).forEach(a -> {
            a.setStatus("RESOLVED");
            alertRepository.save(a);
        });
    }

    // ── Manual generate (scans inventory for LOW_STOCK) ──────────────────────

    public int generateSystemAlerts(Authentication auth) {
        SystemId systemId = getSystemId(auth);
        List<InventoryItem> items = inventoryRepository.findBySystemId(systemId);
        int created = 0;
        for (InventoryItem item : items) {
            if (item.getQuantity() != null
                    && item.getReorderLevel() != null
                    && item.getQuantity() <= item.getReorderLevel()) {

                // Avoid duplicate active low-stock alerts for the same item
                boolean alreadyActive = alertRepository
                    .findBySystemIdAndTargetRoleIn(systemId, List.of("PROCUREMENT", "ALL"))
                    .stream()
                    .anyMatch(a -> "LOW_STOCK".equals(a.getType())
                               && "ACTIVE".equals(a.getStatus())
                               && item.getId().equals(a.getReferenceId()));

                if (!alreadyActive) {
                    createOrderAlert(
                        "LOW_STOCK",
                        "Low stock: " + item.getItemName()
                            + " — " + item.getQuantity() + " "
                            + item.getUnitOfMeasure() + " remaining (reorder level: "
                            + item.getReorderLevel() + ")",
                        item.getId(),
                        "PROCUREMENT"
                    );
                    created++;
                }
            }
        }
        return created;
    }
}
