package com.supplychain.integration_hub;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model for the System1 Procurement → Shipments tab.
 *
 * Derived from inbound {@link Order} documents (purchase orders System1 Procurement
 * raised to the System2 Vendor) that have reached a confirmed/shipping status.
 * Mirrors {@link VendorShipmentView} so the frontend can reuse the same table layout.
 */
@Data
@Builder
public class ProcurementShipmentView {
    private String id;
    private String orderId;
    private String correlationId;
    private SystemId systemId;
    private Direction direction;
    private List<String> itemNames;
    private Integer quantity;
    private String counterpartyName;   // the supplier (System2 Vendor)
    private String supplier;           // alias of counterpartyName for clarity
    private String status;
    private PoStatus poStatus;
    private String shippingStatus;
    private String expectedDeliveryDate;
    private String estimatedDelivery;  // alias of expectedDeliveryDate (frontend compatibility)
    private Double totalAmount;
    private LocalDateTime statusUpdatedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
