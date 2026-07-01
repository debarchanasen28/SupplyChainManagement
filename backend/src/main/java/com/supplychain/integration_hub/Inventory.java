package com.supplychain.integration_hub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    private String id;

    // Identification
    private String inventoryId;
    private SystemId systemId;          // tenant discriminator (SYSTEM1 / SYSTEM2)
    private String productId;
    private String productName;
    private String productCategory;
    private String sku;
    private String barcode;

    // Supplier
    private String supplierId;
    private String supplierName;
    private String supplierEmail;

    // Location
    private String warehouseId;
    private String warehouseName;
    private String warehouseLocation;
    private String binLocation;

    // Stock Levels
    private Integer quantityAvailable;
    private Integer quantityReserved;
    private Integer quantityInTransit;
    private Integer quantityDamaged;
    private Integer totalQuantity;
    private Integer reorderLevel;
    private Integer reorderQuantity;
    private Integer maxStockLevel;
    private Integer minStockLevel;

    // Status
    private String stockStatus;
    private Boolean isActive;

    // Product Details
    private String unitOfMeasure;
    private Double unitCost;
    private Double totalValue;
    private String currency;
    private Double weightPerUnitKg;
    private Double volumePerUnitCubicM;
    private Integer shelfLifeDays;
    private String expiryDate;
    private String batchNumber;
    private String lotNumber;
    private String storageCondition;

    // Audit
    private LocalDateTime lastRestockedAt;
    private LocalDateTime lastAuditedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
