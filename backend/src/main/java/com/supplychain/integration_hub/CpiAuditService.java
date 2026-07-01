package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Writes an IntegrationLog entry for every message crossing the CPI bridge
 * (inbound + outbound) — the enterprise "message monitoring" trail.
 * Auditing must never break the main flow, so failures here are swallowed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CpiAuditService {

    private final IntegrationLogService logService;
    private static final int MAX_PAYLOAD = 2000;

    public void record(String direction, String messageType, String iFlowName,
                       String orderId, String correlationId,
                       String sourceSystem, String targetSystem,
                       String requestPayload, String responsePayload,
                       String status, String errorMessage) {
        record(direction, messageType, iFlowName, orderId, correlationId,
                sourceSystem, targetSystem, requestPayload, responsePayload,
                status, errorMessage, 0, null);
    }

    public void record(String direction, String messageType, String iFlowName,
                       String orderId, String correlationId,
                       String sourceSystem, String targetSystem,
                       String requestPayload, String responsePayload,
                       String status, String errorMessage, Integer retryCount,
                       String mplId) {
        try {
            String eventType = eventType(direction, messageType);
            IntegrationLog entry = IntegrationLog.builder()
                .logId("ILOG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .messageId(correlationId)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .iFlowName(iFlowName)
                .orderId(orderId)
                .sourceSystem(sourceSystem)
                .targetSystem(targetSystem)
                .messageType(messageType)
                .eventType(eventType)
                .message(messageFor(eventType, status))
                .protocol("HTTPS")
                .direction(direction)
                .requestPayload(truncate(requestPayload))
                .responsePayload(truncate(responsePayload))
                .status(status)
                .errorMessage(errorMessage)
                .retryCount(retryCount == null ? 0 : retryCount)
                .mplId(mplId)
                .payloadSizeBytes(requestPayload == null ? 0L : (long) requestPayload.length())
                .processedAt(LocalDateTime.now())
                .build();
            logService.saveLog(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed (non-fatal) corrId={}: {}", correlationId, e.getMessage());
        }
    }

    public void recordCpiEvent(String eventType, String orderId, String correlationId,
                               String sourceSystem, String targetSystem, String status,
                               String message, Integer retryCount, String errorMessage) {
        logService.logIntegrationEvent(correlationId, sourceSystem, targetSystem,
                eventType, status, message, orderId, retryCount, errorMessage, null);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_PAYLOAD ? s : s.substring(0, MAX_PAYLOAD) + "...[truncated]";
    }

    private String eventType(String direction, String messageType) {
        String type = normalize(messageType);
        String suffix = "INBOUND".equalsIgnoreCase(direction) ? "RECEIVED" : "SENT";
        return switch (type) {
            case "PO" -> "PO_" + suffix;
            case "STOCK_OFFER" -> "STOCK_OFFER_" + suffix;
            case "APPROVAL" -> "APPROVAL_" + suffix;
            default -> type + "_" + suffix;
        };
    }

    private String messageFor(String eventType, String status) {
        return eventType.replace('_', ' ').toLowerCase(Locale.ROOT)
                + " with status " + normalize(status);
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }
}
