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
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryItem> createItem(@RequestBody CreateInventoryRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(201).body(inventoryService.createItem(request, authentication));
    }

    @GetMapping
    public ResponseEntity<?> getAllItems(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                inventoryService.getAllItems(authentication, pageable)));
    }

    @PostMapping("/receive")
    public ResponseEntity<?> receiveStock(
            @RequestBody InventoryStockRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(inventoryService.receiveStock(request, authentication));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sellStock(
            @RequestBody InventoryStockRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(inventoryService.sellStock(request, authentication));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> getInventoryAlerts(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                inventoryService.getInventoryAlerts(authentication, pageable)));
    }

    @PatchMapping("/alerts/{id}/resolve")
    public ResponseEntity<?> resolveInventoryAlert(
            @PathVariable String id,
            Authentication authentication) {
        InventoryAlert alert = inventoryService.resolveAlert(id, authentication);
        if (alert == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(alert);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<?> getLowStock(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PaginationSupport.page(
                inventoryService.getLowStockItems(authentication), pageable));
    }

    // Uses MongoDB _id
    @PutMapping("/{id}/quantity")
    public ResponseEntity<InventoryItem> updateQuantity(
            @PathVariable String id,
            @RequestParam int quantity,
            Authentication authentication) {
        try {
            InventoryItem item = inventoryService.updateQuantity(id, quantity, authentication);
            if (item == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Uses MongoDB _id
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable String id,
                                           Authentication authentication) {
        if (!inventoryService.deleteItem(id, authentication)) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
