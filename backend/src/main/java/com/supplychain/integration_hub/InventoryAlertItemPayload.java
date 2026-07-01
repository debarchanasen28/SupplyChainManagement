package com.supplychain.integration_hub;

import lombok.*;

/**
 * One low-stock line item inside the consolidated iFlow5 alert payload.
 * Independent of order/shipment/inventory-update flows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAlertItemPayload {
    private String sku;
    private String itemName;
    private Integer quantity;
    private Integer thresholdQuantity;
    private String unit;
    private String alertLevel;   // BELOW_THRESHOLD | APPROACHING_THRESHOLD
    private String message;
}
