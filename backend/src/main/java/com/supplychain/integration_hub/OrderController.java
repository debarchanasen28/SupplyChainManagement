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
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final System1ProcurementOrderService procurementOrderService;

    // All orders — direction-filtered by role in service layer
    @GetMapping
    public ResponseEntity<?> getAllOrders(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(orderService.getAllOrders(authentication, pageable)));
    }

    // Paginated orders — server-side tenant/role/tab/status/search/sort + page navigation.
    // GET /api/orders/paged?tab=all&status=all&q=&sort=dateDesc&page=0&size=20
    @GetMapping("/paged")
    public ResponseEntity<?> getOrdersPaged(
            Authentication authentication,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String direction) {
        return ResponseEntity.ok(
            orderService.queryOrders(authentication, tab, status, q, sort, page, size, direction));
    }

    // Active orders only (REQUESTED → IN_TRANSIT)
    @GetMapping("/active")
    public ResponseEntity<?> getActiveOrders(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(orderService.getActiveOrders(authentication, pageable)));
    }

    // Past orders (DELIVERED, REJECTED, CANCELLED)
    @GetMapping("/past")
    public ResponseEntity<?> getPastOrders(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(orderService.getPastOrders(authentication, pageable)));
    }

    // Pending approval queue — OUTBOUND orders with status REQUESTED
    // Used by VENDOR to see incoming order requests from simulation
    @GetMapping("/pending-approvals")
    public ResponseEntity<?> getPendingApprovals(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(orderService.getPendingApprovals(authentication, pageable)));
    }

    @GetMapping("/buyer-decisions")
    public ResponseEntity<?> getBuyerDecisions(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(orderService.getBuyerDecisions(authentication, pageable)));
    }

    // Single order by MongoDB _id
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable String id) {
        Order order = orderService.getOrderById(id);
        if (order == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(order);
    }

    // Per-line required-qty vs inventory-on-hand for an order (vendor convenience).
    @GetMapping("/{id}/availability")
    public ResponseEntity<?> availability(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getStockAvailability(id));
    }

    // Create order
    // VENDOR role → creates OUTBOUND order
    // PROCUREMENT role → creates INBOUND order
    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestBody CreateOrderRequest req,
            Authentication authentication) {
        // FLOW B: System1 Procurement → System2 Vendor is owned by System1ProcurementOrderService
        // (auto-dispatch via iFlow1, no manual send, no buyer approval). All other roles keep the
        // existing path (e.g. VENDOR → OUTBOUND order).
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        Order created = "PROCUREMENT".equals(role)
                ? procurementOrderService.createPurchaseOrder(req, authentication)
                : orderService.createOrder(req, authentication);
        return ResponseEntity.ok(created);
    }

    // VENDOR checks inventory and notifies buyer of available stock
    // Body: { "availableQuantity": 150 }
    @PutMapping("/{id}/notify-stock")
    public ResponseEntity<?> notifyStock(
            @PathVariable String id,
            @RequestBody Map<String, Integer> body) {
        Integer qty = body.get("availableQuantity");
        if (qty == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "availableQuantity is required"));
        }
        Order updated = orderService.notifyStockAvailability(id, qty);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    // Buyer responds to stock check notification
    // Body: { "response": "YES" } or { "response": "NO" }
    // In simulation this is called automatically; in My Company PROCUREMENT it is manual
    @PutMapping("/{id}/respond-stock")
    public ResponseEntity<?> respondToStock(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String response = body.get("response");
        if (response == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "response is required — YES or NO"));
        }
        Order updated = orderService.respondToStockCheck(id, response);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    // Send an existing order to the counterparty via CPI iFlow 1.
    // PROCUREMENT/ADMIN trigger this to dispatch a PO; sets poStatus=SENT.
    @PostMapping("/{id}/send")
    public ResponseEntity<?> sendToCpi(@PathVariable String id) {
        Order sent = orderService.sendOrderToCpi(id);
        if (sent == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(sent);
    }

    // VENDOR sends a stock offer (auto qty) to the counterparty procurement via CPI iFlow 3.
    @PutMapping("/{id}/send-offer")
    public ResponseEntity<?> sendOffer(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Order updated;
            if (body != null && body.get("offeredQuantity") instanceof Number quantity) {
                String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
                updated = orderService.sendVendorStockOffer(id, quantity.intValue(), note);
            } else {
                updated = orderService.sendVendorStockOffer(id);
            }
            if (updated == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // VENDOR sends a caller-selected stock offer through CPI iFlow 3.
    // Body: { "offeredQuantity": 120, "note": "" }
    @PostMapping("/{id}/stock-offer")
    public ResponseEntity<?> stockOffer(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Object rawQuantity = body.get("offeredQuantity");
        if (!(rawQuantity instanceof Number quantity)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "offeredQuantity is required and must be numeric"));
        }
        String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
        try {
            Order updated = orderService.sendVendorStockOffer(id, quantity.intValue(), note);
            if (updated == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // VENDOR rejects the PO (cannot supply) and notifies the counterparty via CPI iFlow 3.
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id) {
        try {
            Order updated = orderService.rejectByVendor(id);
            if (updated == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    // VENDOR's final confirmation after the buyer accepted the stock offer — starts fulfilment.
    @PutMapping("/{id}/confirm-supply")
    public ResponseEntity<?> confirmSupply(@PathVariable String id) {
        return confirmVendorSupply(id);
    }

    @PostMapping("/{id}/vendor-confirm-supply")
    public ResponseEntity<?> confirmVendorSupply(@PathVariable String id) {
        try {
            Order updated = orderService.confirmSupply(id);
            if (updated == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel-vendor")
    public ResponseEntity<?> cancelVendor(@PathVariable String id) {
        try {
            Order cancelled = orderService.cancelBySystem1Vendor(id);
            if (cancelled == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(cancelled);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    // Cancel order by MongoDB _id
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable String id,
            Authentication authentication) {
        Order cancelled = orderService.cancelOrder(id, authentication);
        if (cancelled == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(cancelled);
    }
}
