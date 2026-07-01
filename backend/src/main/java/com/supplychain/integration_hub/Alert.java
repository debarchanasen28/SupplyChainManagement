package com.supplychain.integration_hub;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alerts")
public class Alert {

    @Id
    private String id;

    private String alertId;       // Readable ID e.g. ALT-XXXXXXXX

    private String type;          // ORDER_RECEIVED, STOCK_NOTIFIED, ORDER_CONFIRMED,
                                  // ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED,
                                  // SHIPMENT_IN_TRANSIT, SHIPMENT_DELIVERED,
                                  // LOW_STOCK, SHIPMENT_DELAYED

    private String message;

    @Builder.Default
    private String severity = "MEDIUM";   // HIGH, MEDIUM, LOW

    @Builder.Default
    private String status = "ACTIVE";     // ACTIVE, RESOLVED

    // Which role this alert is meant for.
    // "VENDOR", "PROCUREMENT", "ADMIN", or "ALL"
    @Builder.Default
    private String targetRole = "ALL";

    // MongoDB _id of the order/shipment/inventory item this alert relates to
    private String referenceId;

    private SystemId systemId;     // tenant discriminator (SYSTEM1 / SYSTEM2)

    @CreatedDate
    private LocalDateTime createdAt;
}