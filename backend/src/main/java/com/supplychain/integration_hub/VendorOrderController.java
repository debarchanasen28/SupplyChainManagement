package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/vendor/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class VendorOrderController {

    private final OrderService orderService;

    @PatchMapping("/{orderReference}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String orderReference) {
        try {
            Order order = orderService.cancelSystem1VendorOrder(orderReference);
            return order == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(order);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{orderReference}/reject")
    public ResponseEntity<?> reject(@PathVariable String orderReference) {
        try {
            Order order = orderService.rejectSystem1VendorOrder(orderReference);
            return order == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(order);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
