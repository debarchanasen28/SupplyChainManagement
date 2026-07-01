package com.supplychain.integration_hub;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "inventory")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryItem {
    @Id private String id;
    private String inventoryId;
    @Builder.Default
    private SystemId systemId = SystemId.SYSTEM1;
    private String itemName;
    private String sku;
    private String category;
    private Integer quantity;
    @Builder.Default
    private Integer thresholdQuantity = 1000;
    private String unit;
    private LocalDateTime lastUpdatedAt;
    private String lastUpdatedBy;
    // Read-only display metadata for the vendor order view (set by the existing increase/decrease
    // paths; does not change quantities or any flow behaviour).
    private String lastAction;            // INCREASE | DECREASE — direction of the last movement
    private Integer lastQuantityChanged;  // signed delta of the last movement (+received / -supplied)
    @Builder.Default
    private boolean alertSent = false;

    // Legacy aliases retained for existing dashboard and order integrations.
    private String unitOfMeasure;
    private Integer reorderLevel;
    private Double unitPrice;
    private String supplierId;
    private String supplierName;
    private String warehouseLocation;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
