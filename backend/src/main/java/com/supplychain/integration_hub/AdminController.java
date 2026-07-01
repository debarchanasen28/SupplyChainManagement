package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only admin overview of the System 1 business.
 * GET /api/admin/summary, /api/admin/orders, /api/admin/inventory-analysis.
 * No mutations — admin views, never acts.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/summary")
    public ResponseEntity<?> summary(Authentication authentication) {
        return ResponseEntity.ok(adminService.getSummary(Tenant.of(authentication)));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getOrders(Tenant.of(authentication), pageable));
    }

    @GetMapping("/inventory-analysis")
    public ResponseEntity<?> inventoryAnalysis(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getInventoryAnalysis(Tenant.of(authentication), pageable));
    }
}
