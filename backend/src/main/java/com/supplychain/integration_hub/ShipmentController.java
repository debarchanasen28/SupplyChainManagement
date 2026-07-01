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
@RequestMapping("/api/shipments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final VendorShipmentService vendorShipmentService;

    // All shipments — direction-filtered by role
    @GetMapping
    public ResponseEntity<?> getAllShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        if (vendorShipmentService.isSystem1Vendor(authentication)) {
            return ResponseEntity.ok(PagedResponse.from(
                    vendorShipmentService.getShipments(authentication, pageable)));
        }
        return ResponseEntity.ok(PagedResponse.from(
                shipmentService.getAllShipments(authentication, pageable)));
    }

    // Active shipments (PENDING, IN_TRANSIT, OUT_FOR_DELIVERY)
    @GetMapping("/active")
    public ResponseEntity<?> getActiveShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        if (vendorShipmentService.isSystem1Vendor(authentication)) {
            return ResponseEntity.ok(PaginationSupport.page(
                    vendorShipmentService.getActiveShipments(authentication), pageable));
        }
        return ResponseEntity.ok(PagedResponse.from(
                shipmentService.getActiveShipments(authentication, pageable)));
    }

    // Past shipments (DELIVERED, CANCELLED)
    @GetMapping("/past")
    public ResponseEntity<?> getPastShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        if (vendorShipmentService.isSystem1Vendor(authentication)) {
            return ResponseEntity.ok(PaginationSupport.page(
                    vendorShipmentService.getPastShipments(authentication), pageable));
        }
        return ResponseEntity.ok(PagedResponse.from(
                shipmentService.getPastShipments(authentication, pageable)));
    }

    // Single shipment by MongoDB _id
    @GetMapping("/{id}")
    public ResponseEntity<?> getShipmentById(@PathVariable String id, Authentication authentication) {
        Shipment shipment = shipmentService.getShipmentById(id, authentication);
        if (shipment == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(shipment);
    }

    // Create shipment
    // VENDOR → OUTBOUND, PROCUREMENT → INBOUND (set in service)
    @PostMapping
    public ResponseEntity<?> createShipment(
            @RequestBody CreateShipmentRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(shipmentService.createShipment(req, authentication));
    }

    // Update shipment status
    // Body: { "status": "IN_TRANSIT" }
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String status = body.get("status");
        if (status == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "status is required"));
        }
        Shipment updated = shipmentService.updateStatus(id, status, authentication);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    // Cancel shipment
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelShipment(@PathVariable String id, Authentication authentication) {
        Shipment cancelled = shipmentService.cancelShipment(id, authentication);
        if (cancelled == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(cancelled);
    }
}
