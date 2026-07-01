package com.supplychain.integration_hub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "inventory_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAlert {
    @Id
    private String id;
    private String itemName;
    private String sku;
    private Integer currentQuantity;
    private Integer thresholdQuantity;
    private String message;
    @Builder.Default
    private String status = "OPEN";
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    @Builder.Default
    private SystemId systemId = SystemId.SYSTEM1;
}
