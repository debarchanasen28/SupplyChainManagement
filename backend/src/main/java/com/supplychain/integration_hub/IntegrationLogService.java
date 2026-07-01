package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationLogService {

    private final IntegrationLogRepository integrationLogRepository;

    public IntegrationLog saveLog(IntegrationLog log) {
        LocalDateTime now = LocalDateTime.now();
        if (log.getTimestamp() == null) {
            log.setTimestamp(log.getCreatedAt() == null ? now : log.getCreatedAt());
        }
        if (log.getCreatedAt() == null) log.setCreatedAt(log.getTimestamp());
        log.setUpdatedAt(now);
        if (log.getLogId() == null) {
            log.setLogId("ILOG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (log.getCorrelationId() == null) log.setCorrelationId(log.getMessageId());
        if (log.getMessageId() == null) log.setMessageId(log.getCorrelationId());
        if (log.getEventType() == null) log.setEventType(log.getMessageType());
        if (log.getMessageType() == null) log.setMessageType(log.getEventType());
        if (log.getRetryCount() == null) log.setRetryCount(0);
        if (log.getMaxRetries() == null) log.setMaxRetries(3);
        log.setStatus(normalize(log.getStatus() == null ? "SUCCESS" : log.getStatus()));
        return integrationLogRepository.save(log);
    }

    public IntegrationLog createLog(IntegrationLog log) {
        return saveLog(log);
    }

    public IntegrationLog logIntegrationEvent(String correlationId, String sourceSystem,
                                               String targetSystem, String eventType,
                                               String status, String message) {
        return logIntegrationEvent(correlationId, sourceSystem, targetSystem, eventType,
                status, message, null, 0, null, null);
    }

    public IntegrationLog logIntegrationEvent(String correlationId, String sourceSystem,
                                               String targetSystem, String eventType,
                                               String status, String message, String orderId,
                                               Integer retryCount, String errorMessage,
                                               String mplId) {
        try {
            return saveLog(IntegrationLog.builder()
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .messageId(correlationId)
                    .sourceSystem(sourceSystem)
                    .targetSystem(targetSystem)
                    .eventType(normalize(eventType))
                    .messageType(normalize(eventType))
                    .status(normalize(status))
                    .message(message)
                    .orderId(orderId)
                    .retryCount(retryCount == null ? 0 : retryCount)
                    .errorMessage(errorMessage)
                    .mplId(mplId)
                    .processedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Integration log write failed (non-fatal) correlationId={} eventType={}: {}",
                    correlationId, eventType, e.getMessage());
            return null;
        }
    }

    public List<IntegrationLog> getLogs() {
        return integrationLogRepository.findAllByOrderByTimestampDesc();
    }

    public Page<IntegrationLog> getLogs(Pageable pageable) {
        return integrationLogRepository.findAll(pageable);
    }

    public Page<IntegrationLog> getByIFlow(String iFlowName, Pageable pageable) {
        return integrationLogRepository.findByIFlowName(iFlowName, pageable);
    }

    public Page<IntegrationLog> getByOrder(String orderId, Pageable pageable) {
        return integrationLogRepository.findByOrderId(orderId, pageable);
    }

    public Page<IntegrationLog> getByShipment(String shipmentId, Pageable pageable) {
        return integrationLogRepository.findByShipmentId(shipmentId, pageable);
    }

    public Page<IntegrationLog> getBySupplier(String supplierId, Pageable pageable) {
        return integrationLogRepository.findBySupplierId(supplierId, pageable);
    }

    public Page<IntegrationLog> getByStatus(String status, Pageable pageable) {
        return integrationLogRepository.findByStatus(status, pageable);
    }

    public Page<IntegrationLog> getByMessageType(String messageType, Pageable pageable) {
        return integrationLogRepository.findByMessageType(messageType, pageable);
    }

    public Page<IntegrationLog> getByIFlowAndStatus(
            String iFlowName, String status, Pageable pageable) {
        return integrationLogRepository.findByStatusAndIFlowName(status, iFlowName, pageable);
    }

    public Page<IntegrationLog> getLogsByDateRange(
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return integrationLogRepository.findByCreatedAtBetween(from, to, pageable);
    }

    public List<IntegrationLog> getLogsByCorrelationId(String correlationId) {
        return integrationLogRepository.findByCorrelationIdOrderByTimestampDesc(correlationId);
    }

    public List<IntegrationLog> getLogsByStatus(String status) {
        return integrationLogRepository.findByStatusIgnoreCaseOrderByTimestampDesc(status);
    }

    public List<IntegrationLog> getLogsByEventType(String eventType) {
        return integrationLogRepository.findByEventTypeIgnoreCaseOrderByTimestampDesc(eventType);
    }

    public IntegrationLogStats getStats() {
        long total = integrationLogRepository.count();
        long success = integrationLogRepository.countByStatusIgnoreCase("SUCCESS");
        long failed = integrationLogRepository.countByStatusIgnoreCase("FAILED");
        long pending = integrationLogRepository.countByStatusIgnoreCase("PENDING");
        double successRate = total == 0 ? 0.0 : (success * 100.0) / total;
        return new IntegrationLogStats(total, success, failed, pending, successRate);
    }

    public List<IntegrationLog> getAllLogs() {
        return getLogs();
    }

    public Optional<IntegrationLog> getLogById(String logId) {
        return integrationLogRepository.findByLogId(logId);
    }

    public Optional<IntegrationLog> getByMessageId(String messageId) {
        return integrationLogRepository.findByMessageId(messageId);
    }

    public List<IntegrationLog> getByIFlow(String iFlowName) {
        return integrationLogRepository.findByIFlowName(iFlowName);
    }

    public List<IntegrationLog> getByOrder(String orderId) {
        return integrationLogRepository.findByOrderId(orderId);
    }

    public List<IntegrationLog> getByShipment(String shipmentId) {
        return integrationLogRepository.findByShipmentId(shipmentId);
    }

    public List<IntegrationLog> getBySupplier(String supplierId) {
        return integrationLogRepository.findBySupplierId(supplierId);
    }

    public List<IntegrationLog> getByStatus(String status) {
        return getLogsByStatus(status);
    }

    public List<IntegrationLog> getByMessageType(String messageType) {
        return integrationLogRepository.findByMessageType(messageType);
    }

    public List<IntegrationLog> getFailedLogs() {
        return integrationLogRepository.findByStatus("FAILED");
    }

    public List<IntegrationLog> getByIFlowAndStatus(String iFlowName, String status) {
        return integrationLogRepository.findByStatusAndIFlowName(status, iFlowName);
    }

    public List<IntegrationLog> getLogsByDateRange(LocalDateTime from, LocalDateTime to) {
        return integrationLogRepository.findByCreatedAtBetween(from, to);
    }

    public IntegrationLog updateLogStatus(String logId, String status,
                                          String errorCode, String errorMessage,
                                          String errorStack, Long processingTimeMs) {
        Optional<IntegrationLog> existing = integrationLogRepository.findByLogId(logId);
        if (existing.isPresent()) {
            IntegrationLog log = existing.get();
            log.setStatus(status);
            if (errorCode != null) log.setErrorCode(errorCode);
            if (errorMessage != null) log.setErrorMessage(errorMessage);
            if (errorStack != null) log.setErrorStack(errorStack);
            if (processingTimeMs != null) log.setProcessingTimeMs(processingTimeMs);
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                log.setProcessedAt(LocalDateTime.now());
            }
            log.setUpdatedAt(LocalDateTime.now());
            return integrationLogRepository.save(log);
        }
        throw new RuntimeException("Log not found: " + logId);
    }

    public IntegrationLog incrementRetry(String logId) {
        Optional<IntegrationLog> existing = integrationLogRepository.findByLogId(logId);
        if (existing.isPresent()) {
            IntegrationLog log = existing.get();
            int retryCount = log.getRetryCount() != null ? log.getRetryCount() : 0;
            log.setRetryCount(retryCount + 1);
            log.setStatus(retryCount + 1 >= log.getMaxRetries() ? "FAILED" : "RETRY");
            log.setUpdatedAt(LocalDateTime.now());
            return integrationLogRepository.save(log);
        }
        throw new RuntimeException("Log not found: " + logId);
    }

    public void deleteLog(String id) {
        integrationLogRepository.deleteById(id);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
