package com.supplychain.integration_hub;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private String counterpartyId;
    private String counterpartyName;
    private List<OrderItem> items;
    private String expectedDeliveryDate;
    private String notes;
}