package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LogsController {

    private final IntegrationLogRepository integrationLogRepository;

    @GetMapping
    public ResponseEntity<PagedResponse<IntegrationLog>> getLogs(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(integrationLogRepository.findAll(pageable)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<IntegrationLog> logs = integrationLogRepository.findAll();
        long successful = count(logs, "SUCCESS");
        long failed = count(logs, "FAILED");
        long pending = count(logs, "PENDING");
        long total = logs.size();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMessages", total);
        stats.put("successful", successful);
        stats.put("failed", failed);
        stats.put("pending", pending);
        stats.put("successRate", total == 0 ? 0.0 : Math.round(successful * 1000.0 / total) / 10.0);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{correlationId}")
    public ResponseEntity<PagedResponse<IntegrationLog>> getCorrelationTrace(
            @PathVariable String correlationId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                integrationLogRepository.findByMessageId(correlationId, pageable)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PagedResponse<IntegrationLog>> getByStatus(
            @PathVariable String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                integrationLogRepository.findByStatus(status, pageable)));
    }

    private long count(List<IntegrationLog> logs, String status) {
        return logs.stream().filter(log -> status.equalsIgnoreCase(log.getStatus())).count();
    }

}
