package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System1 Procurement → Shipments tab. Surfaces inbound purchase orders that the
 * System2 Vendor has confirmed/shipped. Mirrors {@link VendorShipmentController}.
 */
@RestController
@RequestMapping("/api/procurement/shipments")
@RequiredArgsConstructor
public class ProcurementShipmentController {

    private final ProcurementShipmentService procurementShipmentService;

    @GetMapping
    public ResponseEntity<?> getProcurementShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                procurementShipmentService.getShipments(authentication, pageable)));
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveProcurementShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PaginationSupport.page(
                procurementShipmentService.getActiveShipments(authentication), pageable));
    }

    @GetMapping("/past")
    public ResponseEntity<?> getPastProcurementShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PaginationSupport.page(
                procurementShipmentService.getPastShipments(authentication), pageable));
    }
}
