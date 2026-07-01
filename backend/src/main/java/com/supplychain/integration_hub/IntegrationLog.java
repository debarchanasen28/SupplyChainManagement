package com.supplychain.integration_hub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "integration_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationLog {

    @Id
    private String id;

    @Indexed(name = "integration_log_timestamp_idx")
    private LocalDateTime timestamp;

    @Indexed(name = "integration_log_correlation_id_idx")
    private String correlationId;

    @Indexed(name = "integration_log_event_type_idx")
    private String eventType;

    private String message;
    private String mplId;

    // Identification
    private String logId;
    private String messageId;
    private String iFlowName;
    private String iFlowVersion;

    // References
    private String orderId;
    private String shipmentId;
    private String supplierId;
    private String supplierName;
    private String sourceSystem;
    private String targetSystem;

    // Message Details
    private String messageType;
    private String protocol;
    private String direction;
    private String requestPayload;
    private String responsePayload;
    private List<String> requestHeaders;
    private String endpoint;
    private String httpMethod;
    private Integer httpStatusCode;

    // Status
    @Indexed(name = "integration_log_status_idx")
    private String status;
    private String errorCode;
    private String errorMessage;
    private String errorStack;
    private Integer retryCount;
    private Integer maxRetries;

    // Performance
    private Long processingTimeMs;
    private Long payloadSizeBytes;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime processedAt;
}
