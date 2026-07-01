package com.supplychain.integration_hub;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Approval/rejection callback delivered by CPI iFlow 2. See docs/PO_CONTRACT.md §3. */
@Data
public class ApprovalCallbackRequest {
    @NotBlank(message = "correlationId is required")
    private String correlationId;

    private String poNumber;

    @NotBlank(message = "decision is required (APPROVED or REJECTED)")
    private String decision;    // APPROVED | REJECTED

    private String decidedBy;
    private String reason;
}
