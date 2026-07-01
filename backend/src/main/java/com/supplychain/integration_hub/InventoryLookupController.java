package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only endpoint that returns inventory context for the vendor order view.
 * Lives under /api/inventory/** (already authorized for VENDOR/PROCUREMENT/ADMIN/MANAGER).
 * GET only — no mutation, no side effects.
 */
@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class InventoryLookupController {

    private final InventoryLookupService lookupService;

    @GetMapping("/context")
    public ResponseEntity<InventoryContextView> getContext(
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "itemName", required = false) String itemName) {
        return ResponseEntity.ok(lookupService.lookup(sku, itemName));
    }
}
