package com.supplychain.integration_hub;

/**
 * Shared cross-system PO request/response vocabulary (the integration contract).
 * This is the single status vocabulary that BOTH System 1 and System 2 obey for
 * purchase orders crossing the CPI bridge — distinct from {@link OrderStatus},
 * which drives the internal fulfilment lifecycle (the OrderLifecycleScheduler).
 *
 * Lifecycle: DRAFT -> SENT -> RECEIVED -> APPROVED|REJECTED -> FULFILLED -> SHIPPED
 */
public enum PoStatus {
    DRAFT,      // Procurement is composing the PO (not yet sent)
    SENT,       // Handed to CPI; in transit to the counterparty
    RECEIVED,   // Landed on the counterparty vendor's dashboard, awaiting decision
    APPROVED,   // Vendor approved; result travelling back through CPI
    CONFIRMED,  // Vendor finally confirmed supply after buyer approval
    REJECTED,   // Vendor rejected; result travelling back through CPI
    FULFILLED,  // Post-approval fulfilment started
    SHIPPED,    // Dispatched to buyer (drives the shipping view)
    FAILED      // Outbound dispatch to CPI failed after retries (retryable)
}
