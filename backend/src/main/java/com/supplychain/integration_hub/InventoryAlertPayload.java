package com.supplychain.integration_hub;

import lombok.*;

import java.util.List;

/**
 * Consolidated batch payload POSTed to SAP CPI iFlow5 (one email per scan).
 * Independent feature — never built from or coupled to the order/shipment flows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAlertPayload {
    private String correlationId;          // e.g. alert-20260624-103000
    private String eventType;              // INVENTORY_ALERT_BATCH
    private String generatedAt;            // ISO local date-time string
    private List<Recipient> recipients;
    private Summary summary;
    private List<InventoryAlertItemPayload> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recipient {
        private String role;   // PROCUREMENT | ADMIN | VENDOR
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int totalItems;
        private int belowThresholdCount;
        private int approachingThresholdCount;
    }
}
