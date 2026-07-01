package com.supplychain.integration_hub;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** Canonical PO delivered by CPI to this backend's inbound endpoint. See docs/PO_CONTRACT.md §2. */
@Data
public class InboundPoRequest {
    @NotBlank(message = "correlationId is required")
    private String correlationId;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;

    @NotBlank(message = "poNumber is required")
    private String poNumber;
    private String originSystem;
    private String sourceSystem;
    private String targetSystem;
    private String counterpartyId;
    private String counterpartyName;
    private String format;          // json | xml | csv (already normalized to canonical by CPI)
    private Double totalAmount;
    private List<OrderItem> items;
}
