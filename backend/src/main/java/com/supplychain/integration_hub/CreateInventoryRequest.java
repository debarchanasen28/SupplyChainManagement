package com.supplychain.integration_hub;

import lombok.Data;

@Data
public class CreateInventoryRequest {
    private String itemName;
    private String sku;
    private String category;
    private Integer quantity;
    private String unitOfMeasure;
    private Integer reorderLevel;
    private Double unitPrice;
    private String supplierId;
    private String supplierName;
    private String warehouseLocation;
    private String notes;
}