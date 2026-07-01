package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Decides, on behalf of the (simulated) System2 Vendor, whether to accept or reject a
 * System1 Procurement purchase order.
 *
 * Deterministic rule — reject every 3rd order, approve all others:
 *   1 → APPROVED, 2 → APPROVED, 3 → REJECTED, 4 → APPROVED, 5 → APPROVED, 6 → REJECTED, …
 *
 * The sequence counter is persisted in MongoDB ({@link DecisionCounter}) and incremented
 * atomically, so the pattern continues seamlessly across application restarts.
 *
 * This owns the System1 Procurement → System2 Vendor lane ONLY. It is completely independent
 * of the System2 Procurement → System1 Vendor buyer-approval flow
 * ({@link System2ProcurementDecisionService} / {@link VendorBuyerApprovalService}); never mixed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class System2VendorDecisionService {

    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    // Dedicated, Flow-A-only sequence — never shared with the System1 Vendor buyer-approval flow.
    private static final String COUNTER_ID = "system2-vendor-order-decision";

    private final MongoTemplate mongoTemplate;

    public String decide(Order order) {
        long seq = nextSequence();
        boolean reject = seq % 3 == 0;
        log.info("System2 Vendor deterministic decision seq={} -> {} orderId={} correlationId={}",
                seq, reject ? REJECTED : ACCEPTED, order.getOrderId(), order.getCorrelationId());
        return reject ? REJECTED : ACCEPTED;
    }

    public boolean isAccepted(String decision) {
        return ACCEPTED.equals(decision);
    }

    /** Atomically increment and return the persistent counter (upserts on first use). */
    private long nextSequence() {
        DecisionCounter counter = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(COUNTER_ID)),
                new Update().inc("value", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DecisionCounter.class);
        return counter == null ? 1L : counter.getValue();
    }
}
