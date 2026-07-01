package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IntegrationLogRepository extends MongoRepository<IntegrationLog, String> {
    List<IntegrationLog> findAllByOrderByTimestampDesc();
    List<IntegrationLog> findByCorrelationIdOrderByTimestampDesc(String correlationId);
    List<IntegrationLog> findByStatusIgnoreCaseOrderByTimestampDesc(String status);
    List<IntegrationLog> findByEventTypeIgnoreCaseOrderByTimestampDesc(String eventType);
    long countByStatusIgnoreCase(String status);

    Optional<IntegrationLog> findByLogId(String logId);
    Optional<IntegrationLog> findByMessageId(String messageId);
    List<IntegrationLog> findAllByMessageIdOrderByCreatedAtAsc(String messageId);
    Page<IntegrationLog> findByMessageId(String messageId, Pageable pageable);
    List<IntegrationLog> findByIFlowName(String iFlowName);
    Page<IntegrationLog> findByIFlowName(String iFlowName, Pageable pageable);
    List<IntegrationLog> findByOrderId(String orderId);
    Page<IntegrationLog> findByOrderId(String orderId, Pageable pageable);
    List<IntegrationLog> findByShipmentId(String shipmentId);
    Page<IntegrationLog> findByShipmentId(String shipmentId, Pageable pageable);
    List<IntegrationLog> findBySupplierId(String supplierId);
    Page<IntegrationLog> findBySupplierId(String supplierId, Pageable pageable);
    List<IntegrationLog> findByStatus(String status);
    Page<IntegrationLog> findByStatus(String status, Pageable pageable);
    List<IntegrationLog> findByMessageType(String messageType);
    Page<IntegrationLog> findByMessageType(String messageType, Pageable pageable);
    List<IntegrationLog> findByDirection(String direction);
    List<IntegrationLog> findByProtocol(String protocol);
    List<IntegrationLog> findBySourceSystem(String sourceSystem);
    List<IntegrationLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    Page<IntegrationLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
    List<IntegrationLog> findByStatusAndIFlowName(String status, String iFlowName);
    Page<IntegrationLog> findByStatusAndIFlowName(String status, String iFlowName, Pageable pageable);
}
