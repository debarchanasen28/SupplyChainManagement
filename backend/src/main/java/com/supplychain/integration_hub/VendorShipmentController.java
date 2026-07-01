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

@RestController
@RequestMapping("/api/vendor/shipments")
@RequiredArgsConstructor
public class VendorShipmentController {

    private final VendorShipmentService vendorShipmentService;

    @GetMapping
    public ResponseEntity<?> getVendorShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                vendorShipmentService.getShipments(authentication, pageable)));
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveVendorShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PaginationSupport.page(
                vendorShipmentService.getActiveShipments(authentication), pageable));
    }

    @GetMapping("/past")
    public ResponseEntity<?> getPastVendorShipments(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PaginationSupport.page(
                vendorShipmentService.getPastShipments(authentication), pageable));
    }
}
