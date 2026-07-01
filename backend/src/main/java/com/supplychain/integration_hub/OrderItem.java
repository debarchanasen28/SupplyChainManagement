package com.supplychain.integration_hub;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private String description;
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;

    // --- Line snapshot (captured from inventory at order-creation time; price never trusted from FE) ---
    private String sku;
    private String itemName;
    private String unit;
    private Double lineTotal;
}