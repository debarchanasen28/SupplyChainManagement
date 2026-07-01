package com.supplychain.integration_hub;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    private String orderId;              // Readable ID e.g. ORD-XXXXXXXX

    private Direction direction;         // INBOUND (procurement) or OUTBOUND (vendor)
    private OrderStatus status;          // Internal fulfilment lifecycle (scheduler-driven)

    // --- Cross-system integration contract (the CPI bridge) ---
    // Shared PO request/response status that both systems obey. See PoStatus.
    private PoStatus poStatus;

    // Immutable correlation key that survives every format transform (JSON<->XML<->canonical).
    // The approval/rejection callback references this so CPI routes the response to the exact PO.
    private String correlationId;

    // Write-once dedup key for inbound endpoints — survives the System 2 ~2min timer flood.
    private String idempotencyKey;

    // Which system owns/raised this PO, and which system it is addressed to ("system1"/"system2").
    private String sourceSystem;
    private String targetSystem;

    // The system this record physically belongs to on shared infra (isolation discriminator).
    private SystemId systemId;
    // Wire format the procurement side chose for this PO: "json" | "xml" | "csv".
    private String format;

    // ML risk score (0-100) assigned when the PO is raised (iFlow 5).
    private Double riskScore;

    // The external party on the other side of this order.
    // OUTBOUND: the buyer we are selling to.
    // INBOUND:  the supplier we are buying from.
    private String counterpartyId;
    private String counterpartyName;

    private List<OrderItem> items;
    private Double totalAmount;

    private String expectedDeliveryDate;
    private String notes;

    // --- Stock-check negotiation (OUTBOUND orders only) ---
    // Step 1: VENDOR checks inventory and clicks "Notify Availability"
    @Builder.Default
    private boolean stockCheckSent = false;

    // How many units the vendor says are available
    private Integer availableQuantity;

    // Step 2: Buyer responds — "YES" | "NO" | null (not yet responded)
    private String buyerResponse;

    // System2 Procurement buyer decision applied via the iFlow2 callback (Flow A).
    private String buyerDecision;        // "APPROVED" | "REJECTED"
    private String buyerDecisionReason;  // human-readable reason

    // Who cancelled/rejected this order: SYSTEM1_VENDOR, SYSTEM1_PROCUREMENT,
    // SYSTEM2_VENDOR, SYSTEM2_PROCUREMENT, SYSTEM_AUTO
    private String cancelledBy;
    private String cancellationReason;
    private String vendorDecision;
    private String rejectionReason;
    private String vendorFinalDecision;

    // --- Lifecycle timestamps (used by OrderLifecycleScheduler) ---
    // Stores when the order last changed status so the scheduler
    // knows when to fire the next transition.
    private LocalDateTime statusUpdatedAt;
    private LocalDateTime resolvedAt;

    @CreatedDate
    private LocalDateTime createdAt;
}
