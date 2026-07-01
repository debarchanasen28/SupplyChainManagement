package com.supplychain.integration_hub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only projection of a vendor inventory item exposed to System1 Procurement while it builds a
 * purchase order. Intentionally a thin DTO — it carries no mutable handles (no Mongo _id) so it can
 * never be used to update inventory. unitPrice is the fixed catalogue price.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorInventoryView {
    private String systemId;
    private String sku;
    private String itemName;
    private Integer availableQuantity;
    private Integer thresholdQuantity;
    private String unit;
    private Double unitPrice;

    public static VendorInventoryView from(InventoryItem item) {
        return VendorInventoryView.builder()
                .systemId(item.getSystemId() != null ? item.getSystemId().name() : null)
                .sku(item.getSku())
                .itemName(item.getItemName())
                .availableQuantity(item.getQuantity())
                .thresholdQuantity(item.getThresholdQuantity())
                .unit(item.getUnit() != null ? item.getUnit() : item.getUnitOfMeasure())
                .unitPrice(item.getUnitPrice())
                .build();
    }
}
