package com.supplychain.integration_hub;

import lombok.Data;

/** Stock-offer / reject delivered by CPI iFlow 3 (vendor -> counterparty procurement). */
@Data
public class StockOfferRequest {
    private String correlationId;
    private String poNumber;
    private String sourceSystem;   // vendor side, e.g. system1
    private String targetSystem;   // procurement side, e.g. system2
    private String decision;       // OFFER | REJECT
    private Integer offeredQuantity;
    private Integer requiredQuantity;
    private String note;
}
