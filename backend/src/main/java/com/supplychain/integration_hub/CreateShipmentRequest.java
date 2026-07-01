package com.supplychain.integration_hub;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShipmentRequest {
    private String orderId;
    private String counterpartyId;
    private String counterpartyName;
    private String carrier;
    private String trackingNumber;
    private String origin;
    private String destination;
    private String estimatedDelivery;
    private String notes;
}