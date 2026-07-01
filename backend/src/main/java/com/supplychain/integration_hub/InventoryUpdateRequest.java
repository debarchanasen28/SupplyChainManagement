package com.supplychain.integration_hub;

import lombok.Data;

/**
 * Inventory movement delivered by CPI iFlow 4 (inventory synchronization).
 *   eventType = VENDOR_SUPPLY      -> DECREASE System1 inventory (vendor supplied goods)
 *   eventType = PROCUREMENT_RECEIVE -> INCREASE System1 inventory (procurement received goods)
 */
@Data
public class InventoryUpdateRequest {
    private String correlationId;
    private String orderId;
    private String eventType;   // VENDOR_SUPPLY | PROCUREMENT_RECEIVE
    private String sku;
    private String itemName;
    private Integer quantity;
    private String unit;
    private String reason;
}
