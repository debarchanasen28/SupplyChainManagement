package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/integration-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IntegrationLogController {

    private final IntegrationLogService integrationLogService;

    @PostMapping
    public ResponseEntity<IntegrationLog> createLog(@RequestBody IntegrationLog log) {
        IntegrationLog saved = integrationLogService.createLog(log);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<?> getAllLogs(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogService.getLogs(pageable)));
    }

    @GetMapping("/{logId}")
    public ResponseEntity<IntegrationLog> getLog(@PathVariable String logId) {
        Optional<IntegrationLog> log = integrationLogService.getLogById(logId);
        return log.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/message/{messageId}")
    public ResponseEntity<IntegrationLog> getByMessageId(@PathVariable String messageId) {
        Optional<IntegrationLog> log = integrationLogService.getByMessageId(messageId);
        return log.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/iflow/{iFlowName}")
    public ResponseEntity<?> getByIFlow(
            @PathVariable String iFlowName,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogService.getByIFlow(iFlowName, pageable)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getByOrder(
            @PathVariable String orderId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogService.getByOrder(orderId, pageable)));
    }

    @GetMapping("/shipment/{shipmentId}")
    public ResponseEntity<?> getByShipment(
            @PathVariable String shipmentId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogService.getByShipment(shipmentId, pageable)));
    }

    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<?> getBySupplier(
            @PathVariable String supplierId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogService.getBySupplier(supplierId, pageable)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getByStatus(
            @PathVariable String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogService.getByStatus(status, pageable)));
    }

    @GetMapping("/type/{messageType}")
    public ResponseEntity<?> getByMessageType(
            @PathVariable String messageType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogService.getByMessageType(messageType, pageable)));
    }

    @GetMapping("/failed")
    public ResponseEntity<?> getFailedLogs(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                integrationLogService.getByStatus("FAILED", pageable)));
    }

    @GetMapping("/iflow/{iFlowName}/status/{status}")
    public ResponseEntity<?> getByIFlowAndStatus(
            @PathVariable String iFlowName,
            @PathVariable String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                integrationLogService.getByIFlowAndStatus(iFlowName, status, pageable)));
    }

    @GetMapping("/date-range")
    public ResponseEntity<?> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                integrationLogService.getLogsByDateRange(from, to, pageable)));
    }

    @PatchMapping("/{logId}/status")
    public ResponseEntity<IntegrationLog> updateStatus(
            @PathVariable String logId,
            @RequestParam String status,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) String errorMessage,
            @RequestParam(required = false) String errorStack,
            @RequestParam(required = false) Long processingTimeMs) {
        try {
            IntegrationLog updated = integrationLogService.updateLogStatus(
                    logId, status, errorCode, errorMessage, errorStack, processingTimeMs);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{logId}/retry")
    public ResponseEntity<IntegrationLog> incrementRetry(@PathVariable String logId) {
        try {
            IntegrationLog updated = integrationLogService.incrementRetry(logId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLog(@PathVariable String id) {
        integrationLogService.deleteLog(id);
        return ResponseEntity.noContent().build();
    }
}
