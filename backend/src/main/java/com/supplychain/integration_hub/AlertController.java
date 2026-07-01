package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // All alerts — role-filtered
    @GetMapping
    public ResponseEntity<?> getAllAlerts(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                alertService.getAllAlerts(authentication, pageable)));
    }

    // Active alerts only
    @GetMapping("/active")
    public ResponseEntity<?> getActiveAlerts(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                alertService.getActiveAlerts(authentication, pageable)));
    }

    // Resolve a single alert by MongoDB _id
    @PutMapping("/{id}/resolve")
    public ResponseEntity<?> resolveAlert(@PathVariable String id, Authentication authentication) {
        Alert resolved = alertService.resolveAlert(id, authentication);
        if (resolved == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(resolved);
    }

    // Resolve all active alerts for this role
    @PutMapping("/resolve-all")
    public ResponseEntity<?> resolveAll(Authentication authentication) {
        alertService.resolveAll(authentication);
        return ResponseEntity.ok(Map.of("message", "All active alerts resolved"));
    }

    // Manually trigger system alert scan (LOW_STOCK check)
    @PostMapping("/generate")
    public ResponseEntity<?> generateAlerts(Authentication authentication) {
        int count = alertService.generateSystemAlerts(authentication);
        return ResponseEntity.ok(Map.of("generated", count));
    }
}
