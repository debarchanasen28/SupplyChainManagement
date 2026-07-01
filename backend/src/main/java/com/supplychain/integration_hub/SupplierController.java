package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    public ResponseEntity<Supplier> create(@RequestBody CreateSupplierRequest request,
                                           Authentication authentication) {
        return ResponseEntity.ok(supplierService.createSupplier(request, authentication));
    }

    @GetMapping
    public ResponseEntity<List<Supplier>> getAll(Authentication authentication) {
        return ResponseEntity.ok(supplierService.getAllSuppliers(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getById(@PathVariable String id,
                                            Authentication authentication) {
        Supplier supplier = supplierService.getSupplierById(id, authentication);
        if (supplier == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(supplier);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> update(@PathVariable String id,
                                            @RequestBody UpdateSupplierRequest request,
                                            Authentication authentication) {
        Supplier updated = supplierService.updateSupplier(id, request, authentication);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Supplier> updateStatus(@PathVariable String id,
                                                  @RequestBody Map<String, String> body,
                                                  Authentication authentication) {
        Supplier updated = supplierService.updateStatus(id, body.get("status"), authentication);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       Authentication authentication) {
        boolean deleted = supplierService.deleteSupplier(id, authentication);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
