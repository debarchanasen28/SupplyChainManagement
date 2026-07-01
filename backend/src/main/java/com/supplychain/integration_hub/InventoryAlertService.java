package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Inventory low-stock alert feature (isolated).
 *
 * Scans System1 inventory, classifies low / approaching-low items, and sends ONE consolidated batch
 * to SAP CPI iFlow5 (which emails Procurement/Admin/Vendor). Also raises role-targeted dashboard
 * alerts. Never sends email directly and never touches order/shipment/inventory-update flows.
 *
 * Dedup: keeps the last sent signature (SKU+quantity set) and timestamp in memory. The same
 * unreplenished set is not re-emailed within 30 minutes; after 30 minutes it is re-sent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAlertService {

    private final InventoryRepository inventoryRepository;
    private final CpiInventoryAlertClient alertClient;
    private final AlertService alertService;

    @Value("${alert.inventory.recipient.procurement:proc1@sys1.com}")        private String procurementEmail;
    @Value("${alert.inventory.recipient.admin:debarchana.sen28@gmail.com}")  private String adminEmail;
    @Value("${alert.inventory.recipient.vendor:vendor1@sys1.com}")           private String vendorEmail;

    private static final long RESEND_AFTER_MS = 30 * 60 * 1000L;   // 30 minutes
    private static final DateTimeFormatter TS  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter CID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // In-memory dedup state (simple, per the requirement).
    private volatile String lastSignature = null;
    private volatile Instant lastSentAt = Instant.EPOCH;

    public void scanAndAlert() {
        log.info("Inventory alert scan started");

        List<InventoryItem> all = inventoryRepository.findBySystemId(SystemId.SYSTEM1);
        List<InventoryAlertItemPayload> items = new ArrayList<>();
        int below = 0, approaching = 0;

        for (InventoryItem it : all) {
            if (it.getQuantity() == null || it.getThresholdQuantity() == null) continue;
            int q = it.getQuantity();
            int t = it.getThresholdQuantity();
            double upper = t * 1.2;

            String level;
            if (q < t) {
                level = "BELOW_THRESHOLD";
                below++;
            } else if (q <= upper) {
                level = "APPROACHING_THRESHOLD";
                approaching++;
            } else {
                continue;   // above threshold * 1.2 -> ignore
            }

            String message = "BELOW_THRESHOLD".equals(level)
                ? "Critical stock alert: " + it.getItemName() + " is below threshold. Current quantity: "
                    + q + ", threshold: " + t + "."
                : "Stock approaching threshold: " + it.getItemName() + ". Current quantity: "
                    + q + ", threshold: " + t + ".";

            items.add(InventoryAlertItemPayload.builder()
                .sku(it.getSku())
                .itemName(it.getItemName())
                .quantity(q)
                .thresholdQuantity(t)
                .unit(it.getUnit() == null || it.getUnit().isBlank() ? "pcs" : it.getUnit())
                .alertLevel(level)
                .message(message)
                .build());
        }

        log.info("Inventory alert items found count={}", items.size());
        if (items.isEmpty()) {
            log.info("No alert email sent; no low-stock items");
            return;
        }

        String signature = buildSignature(items);
        long sinceLastMs = System.currentTimeMillis() - lastSentAt.toEpochMilli();
        if (signature.equals(lastSignature) && sinceLastMs < RESEND_AFTER_MS) {
            log.info("Inventory alert duplicate — same SKUs/quantities within 30min, skipping email count={}",
                items.size());
            return;
        }

        InventoryAlertPayload payload = buildPayload(items, below, approaching);
        try {
            alertClient.sendAlertEmail(payload);
            lastSignature = signature;
            lastSentAt = Instant.now();
            createDashboardAlerts(items.size(), below, approaching);
        } catch (Exception e) {
            log.warn("CPI iFlow5 alert email failed — will retry next scan: {}", e.getMessage());
        }
    }

    private InventoryAlertPayload buildPayload(List<InventoryAlertItemPayload> items,
                                               int below, int approaching) {
        LocalDateTime now = LocalDateTime.now();
        return InventoryAlertPayload.builder()
            .correlationId("alert-" + now.format(CID))
            .eventType("INVENTORY_ALERT_BATCH")
            .generatedAt(now.format(TS))
            .recipients(List.of(
                InventoryAlertPayload.Recipient.builder().role("PROCUREMENT").email(procurementEmail).build(),
                InventoryAlertPayload.Recipient.builder().role("ADMIN").email(adminEmail).build(),
                InventoryAlertPayload.Recipient.builder().role("VENDOR").email(vendorEmail).build()
            ))
            .summary(InventoryAlertPayload.Summary.builder()
                .totalItems(items.size())
                .belowThresholdCount(below)
                .approachingThresholdCount(approaching)
                .build())
            .items(items)
            .build();
    }

    /** Stable signature of the affected set: sorted "sku=qty" pairs. Detects replenishment changes. */
    private String buildSignature(List<InventoryAlertItemPayload> items) {
        return items.stream()
            .map(i -> (i.getSku() == null ? i.getItemName() : i.getSku()) + "=" + i.getQuantity())
            .sorted()
            .collect(Collectors.joining(";"));
    }

    private void createDashboardAlerts(int total, int below, int approaching) {
        String message = "Inventory alert: " + total + " item(s) need attention ("
            + below + " below threshold, " + approaching + " approaching).";
        for (String role : List.of("VENDOR", "PROCUREMENT", "ADMIN")) {
            alertService.createOrderAlert("LOW_STOCK", message, "INVENTORY_ALERT", role);
        }
        log.info("Dashboard alerts created for VENDOR, PROCUREMENT and ADMIN");
    }
}
