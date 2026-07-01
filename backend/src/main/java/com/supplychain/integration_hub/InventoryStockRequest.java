package com.supplychain.integration_hub;

import lombok.Data;

@Data
public class InventoryStockRequest {
    private String itemName;
    private String sku;
    private Integer quantity;
    private String reason;
}
