package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * READ-ONLY visibility of the System2 Vendor inventory for System1 Procurement (Flow B PO creation).
 *
 * This controller never mutates inventory and never returns System1 stock — it only lets System1
 * Procurement see what the System2 Vendor has available (and at what fixed price) while building a
 * purchase order. It is deliberately separate from the System2 Procurement random generator, which
 * reads System1 Vendor inventory instead.
 */
@RestController
@RequestMapping("/api/procurement")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProcurementVendorInventoryController {

    private final InventoryService inventoryService;

    // Primary endpoint.
    @GetMapping("/system2-vendor-inventory")
    public ResponseEntity<List<VendorInventoryView>> getSystem2VendorInventory() {
        return ResponseEntity.ok(
                inventoryService.getSystem2VendorInventory().stream()
                        .map(VendorInventoryView::from)
                        .toList());
    }

    // Backwards-compatible alias for the originally specified path.
    @GetMapping("/vendor-inventory/system2")
    public ResponseEntity<List<VendorInventoryView>> getSystem2VendorInventoryAlias() {
        return getSystem2VendorInventory();
    }
}
