package com.supplychain.integration_hub;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints CPI calls INTO this backend.
 * Permitted in SecurityConfig (CPI cannot carry a user JWT), but gated by a
 * shared secret (X-CPI-Secret) when cpi.inbound-secret is configured.
 *   POST /api/cpi/inbound/po        — receive a PO from the counterparty
 *   POST /api/cpi/inbound/approval  — receive an approval/rejection callback
 */
@RestController
@RequestMapping("/api/cpi/inbound")
@RequiredArgsConstructor
@Slf4j
public class CpiInboundController {

    private final CpiInboundService inboundService;
    private final InventoryService inventoryService;

    @Value("${cpi.inbound-secret}")
    private String inboundSecret;

    /** Returns true if the request should be rejected for a bad/missing secret. */
    private boolean denied(String provided) {
        if (inboundSecret == null || inboundSecret.isBlank()) return false; // enforcement off (dev)
        return !inboundSecret.equals(provided);
    }

    @PostMapping("/po")
    public ResponseEntity<?> receivePo(
            @RequestHeader(value = "X-CPI-Secret", required = false) String secret,
            @Valid @RequestBody InboundPoRequest req) {
        if (denied(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing X-CPI-Secret"));
        }
        try {
            Order o = inboundService.receivePo(req);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", o.getPoStatus() == null ? "RECEIVED" : o.getPoStatus().name());
            body.put("orderId", o.getOrderId());
            body.put("correlationId", o.getCorrelationId());
            return ResponseEntity.ok(body);
        } catch (OpenPoCapExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/stock-offer")
    public ResponseEntity<?> receiveStockOffer(
            @RequestHeader(value = "X-CPI-Secret", required = false) String secret,
            @RequestBody StockOfferRequest req) {
        log.info("stock-offer endpoint reached");
        log.info("X-CPI-Secret present={}", secret != null && !secret.isBlank());
        log.info("CPI inbound stock-offer endpoint hit correlationId={} poNumber={} decision={} offered={}",
            req.getCorrelationId(), req.getPoNumber(), req.getDecision(), req.getOfferedQuantity());
        if (denied(secret)) {
            log.warn("stock-offer rejected reason=invalid-or-missing-X-CPI-Secret correlationId={}",
                req.getCorrelationId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing X-CPI-Secret"));
        }
        Order o;
        try {
            o = inboundService.receiveStockOffer(req);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            if ("Order already cancelled".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Order already cancelled"));
            }
            throw e;
        }
        log.info("stock-offer accepted correlationId={} orderId={} status={}",
            req.getCorrelationId(), o == null ? null : o.getOrderId(), o == null ? null : o.getStatus());
        if (o == null) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "No order found for correlationId " + req.getCorrelationId()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", o.getStatus() == null ? null : o.getStatus().name());
        body.put("orderId", o.getOrderId());
        body.put("correlationId", o.getCorrelationId());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/inventory-update")
    public ResponseEntity<?> receiveInventoryUpdate(
            @RequestHeader(value = "X-CPI-Secret", required = false) String secret,
            @RequestBody InventoryUpdateRequest req) {
        log.info("inventory-update endpoint reached");
        log.info("X-CPI-Secret present={}", secret != null && !secret.isBlank());
        log.info("CPI inbound inventory-update hit correlationId={} orderId={} eventType={} itemName={} sku={} quantity={}",
            req.getCorrelationId(), req.getOrderId(), req.getEventType(),
            req.getItemName(), req.getSku(), req.getQuantity());
        if (denied(secret)) {
            log.warn("inventory-update rejected reason=invalid-or-missing-X-CPI-Secret correlationId={}",
                req.getCorrelationId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing X-CPI-Secret"));
        }
        String inventoryAction = "VENDOR_SUPPLY".equalsIgnoreCase(req.getEventType()) ? "DECREASE"
            : "PROCUREMENT_RECEIVE".equalsIgnoreCase(req.getEventType()) ? "INCREASE" : "UNKNOWN";
        InventoryItem item;
        try {
            item = inventoryService.applyInventoryEvent(
                req.getEventType(), req.getItemName(), req.getSku(),
                req.getQuantity() == null ? 0 : req.getQuantity(), req.getReason());
        } catch (IllegalArgumentException e) {
            log.warn("inventory-update rejected correlationId={} action={}: {}",
                req.getCorrelationId(), inventoryAction, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage(), "inventoryAction", inventoryAction));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        body.put("correlationId", req.getCorrelationId());
        body.put("orderId", req.getOrderId());
        body.put("eventType", req.getEventType());
        body.put("inventoryAction", inventoryAction);
        body.put("itemName", item.getItemName());
        body.put("sku", item.getSku());
        body.put("newQuantity", item.getQuantity());
        log.info("inventory-update accepted correlationId={} action={} item={} newQuantity={}",
            req.getCorrelationId(), inventoryAction, item.getItemName(), item.getQuantity());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/approval")
    public ResponseEntity<?> receiveApproval(
            @RequestHeader(value = "X-CPI-Secret", required = false) String secret,
            @Valid @RequestBody ApprovalCallbackRequest req) {
        log.info("CPI inbound approval endpoint hit correlationId={} poNumber={} decision={} decidedBy={}",
            req.getCorrelationId(), req.getPoNumber(), req.getDecision(), req.getDecidedBy());
        if (denied(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing X-CPI-Secret"));
        }
        Order o = inboundService.receiveApproval(req);
        if (o == null) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "No PO found for correlationId " + req.getCorrelationId()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", o.getPoStatus().name());
        body.put("orderId", o.getOrderId());
        body.put("correlationId", o.getCorrelationId());
        log.info("CPI inbound approval response orderId={} correlationId={} status={}",
            o.getOrderId(), o.getCorrelationId(), o.getPoStatus());
        return ResponseEntity.ok(body);
    }
}
