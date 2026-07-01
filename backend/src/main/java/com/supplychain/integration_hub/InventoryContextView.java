package com.supplychain.integration_hub;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Read-only inventory context for the vendor order expanded view. Pure display data — never used to
 * change stock or drive any business process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryContextView {
    private boolean found;
    private String itemName;
    private String sku;
    private Integer currentQuantity;
    private Integer thresholdQuantity;
    private String status;        // HEALTHY | APPROACHING_THRESHOLD | BELOW_THRESHOLD
    private String statusColor;   // GREEN | YELLOW | RED
    private String unit;
    private LocalDateTime lastUpdatedAt;
    private String lastAction;            // INCREASE | DECREASE | null
    private Integer lastQuantityChanged;  // signed delta of last movement
}
