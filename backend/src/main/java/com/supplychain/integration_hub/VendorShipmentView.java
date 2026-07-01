package com.supplychain.integration_hub;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VendorShipmentView {
    private String id;
    private String shipmentId;
    private String orderId;
    private String correlationId;
    private SystemId systemId;
    private Direction direction;
    private List<String> itemNames;
    private Integer quantity;
    private String buyer;
    private String customer;
    private String counterpartyName;
    private String status;
    private String shippingStatus;
    private String expectedDeliveryDate;
    private String estimatedDelivery;
    private LocalDateTime updatedAt;
    private LocalDateTime statusUpdatedAt;
    private String buyerResponse;
    private boolean vendorConfirmed;
}
