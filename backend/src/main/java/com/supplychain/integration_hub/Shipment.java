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
@Document(collection = "shipments")
public class Shipment {

    @Id
    private String id;

    private String shipmentId;           // Readable ID e.g. SHP-XXXXXXXX

    private Direction direction;         // INBOUND (procurement) or OUTBOUND (vendor)
    private SystemId systemId;           // tenant discriminator (SYSTEM1 / SYSTEM2)

    // Linked order
    private String orderId;

    // The external party on the other side of this shipment.
    // OUTBOUND: the buyer receiving the goods.
    // INBOUND:  the supplier sending the goods.
    private String counterpartyId;
    private String counterpartyName;

    private String carrier;
    private String trackingNumber;

    private String origin;
    private String destination;

    @Builder.Default
    private String status = "PENDING";   // PENDING, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, CANCELLED

    private String estimatedDelivery;
    private String actualDelivery;

    private String delayReason;
    private String notes;

    @CreatedDate
    private LocalDateTime createdAt;
}