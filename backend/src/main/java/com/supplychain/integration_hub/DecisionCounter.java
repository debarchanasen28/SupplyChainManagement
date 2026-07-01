package com.supplychain.integration_hub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persistent, monotonically-increasing counter stored in MongoDB so a decision sequence
 * survives application restarts. One document per named sequence (the {@code _id}).
 *
 * Used by {@link System2VendorDecisionService} to deterministically reject every 3rd
 * System1 Procurement → System2 Vendor order.
 */
@Document(collection = "decision_counters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionCounter {

    @Id
    private String id;       // the sequence name

    private long value;      // current count
}
