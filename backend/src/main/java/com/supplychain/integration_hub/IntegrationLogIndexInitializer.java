package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IntegrationLogIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        var indexes = mongoTemplate.indexOps(IntegrationLog.class);
        indexes.createIndex(new Index().on("correlationId", Sort.Direction.ASC)
                .named("integration_log_correlation_id_idx"));
        indexes.createIndex(new Index().on("timestamp", Sort.Direction.DESC)
                .named("integration_log_timestamp_idx"));
        indexes.createIndex(new Index().on("eventType", Sort.Direction.ASC)
                .named("integration_log_event_type_idx"));
        indexes.createIndex(new Index().on("status", Sort.Direction.ASC)
                .named("integration_log_status_idx"));
    }
}
