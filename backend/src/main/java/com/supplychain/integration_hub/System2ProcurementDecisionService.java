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
 * FLOW A — System2 Procurement → System1 Vendor.
 *
 * Decides, on behalf of the (simulated) System2 Procurement buyer, whether to approve or reject a
 * System1 Vendor stock offer. Deterministic: every 3rd order is BUYER_REJECTED, all others
 * BUYER_APPROVED. The sequence is persisted in MongoDB ({@link DecisionCounter}) so the pattern
 * survives restarts.
 *
 * Flow-A only. Has nothing to do with the System1 Procurement → System2 Vendor lane
 * (that is {@link System2VendorDecisionService}). The two are never mixed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class System2ProcurementDecisionService {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    // Dedicated Flow-A buyer-decision counter — never shared with Flow B.
    private static final String COUNTER_ID = "system2-procurement-buyer-decision";

    private final MongoTemplate mongoTemplate;

    public String decide(Order order) {
        long seq = nextSequence();
        boolean reject = seq % 3 == 0;
        log.info("System2 Procurement buyer decision seq={} -> {} orderId={} correlationId={}",
                seq, reject ? REJECTED : APPROVED, order.getOrderId(), order.getCorrelationId());
        return reject ? REJECTED : APPROVED;
    }

    public boolean isApproved(String decision) {
        return APPROVED.equals(decision);
    }

    private long nextSequence() {
        DecisionCounter counter = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(COUNTER_ID)),
                new Update().inc("value", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DecisionCounter.class);
        return counter == null ? 1L : counter.getValue();
    }
}
