package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles POs and approval callbacks arriving FROM CPI.
 * Idempotency: inbound POs dedup on idempotencyKey; approvals are applied once.
 * Hardened with an open-PO cap and full audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CpiInboundService {

    private final OrderRepository orderRepository;
    private final AlertService alertService;
    private final CpiAuditService audit;
    private final VendorBuyerApprovalService vendorBuyerApprovalService;

    @Value("${cpi.max-open-pos}")
    private long maxOpenPos;

    private String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String vendorActorFor(Order order) {
        return ("system2".equalsIgnoreCase(order.getTargetSystem()) ? "SYSTEM2" : "SYSTEM1") + "_VENDOR";
    }

    private String cancellationActorFor(Order order, ApprovalCallbackRequest req) {
        if (req.getDecidedBy() != null && req.getDecidedBy().startsWith("SYSTEM")) {
            return req.getDecidedBy();
        }
        return vendorActorFor(order);
    }

    private String alertTargetFor(Order order) {
        return order.getDirection() == Direction.OUTBOUND ? "VENDOR" : "PROCUREMENT";
    }

    private Order findSystem2Mirror(Order source, Direction direction) {
        if (source.getCorrelationId() != null) {
            Order byCorrelation = orderRepository.findByCorrelationIdAndSystemId(
                source.getCorrelationId(),
                SystemId.SYSTEM2
            );
            if (byCorrelation != null) return byCorrelation;
        }
        return orderRepository.findBySystemIdAndOrderIdAndDirection(
            SystemId.SYSTEM2,
            source.getOrderId(),
            direction
        );
    }

    private void syncSystem2Mirror(Order source) {
        if (source.getSystemId() != SystemId.SYSTEM1) return;

        Direction mirrorDirection = source.getDirection() == Direction.OUTBOUND
            ? Direction.INBOUND
            : Direction.OUTBOUND;

        Order mirror = findSystem2Mirror(source, mirrorDirection);
        if (mirror == null) {
            mirror = Order.builder()
                .orderId(source.getOrderId())
                .direction(mirrorDirection)
                .systemId(SystemId.SYSTEM2)
                .createdAt(source.getCreatedAt() != null ? source.getCreatedAt() : LocalDateTime.now())
                .build();
        }

        mirror.setStatus(source.getStatus());
        mirror.setPoStatus(source.getPoStatus());
        mirror.setCorrelationId(source.getCorrelationId());
        mirror.setIdempotencyKey(null);
        mirror.setSourceSystem(source.getSourceSystem());
        mirror.setTargetSystem(source.getTargetSystem());
        mirror.setFormat(source.getFormat());
        mirror.setCounterpartyId(source.getDirection() == Direction.OUTBOUND ? "S1-VENDOR" : "S1-PROC");
        mirror.setCounterpartyName(source.getDirection() == Direction.OUTBOUND
            ? "System 1 Vendor"
            : "System 1 Procurement");
        mirror.setExpectedDeliveryDate(source.getExpectedDeliveryDate());
        mirror.setItems(source.getItems());
        mirror.setTotalAmount(source.getTotalAmount());
        mirror.setNotes(source.getNotes());
        mirror.setStockCheckSent(source.isStockCheckSent());
        mirror.setAvailableQuantity(source.getAvailableQuantity());
        mirror.setBuyerResponse(source.getBuyerResponse());
        mirror.setCancelledBy(source.getCancelledBy());
        mirror.setResolvedAt(source.getResolvedAt());
        mirror.setStatusUpdatedAt(source.getStatusUpdatedAt());
        orderRepository.save(mirror);
    }

    /** A PO raised by the counterparty's procurement lands here for OUR vendor to approve. */
    public Order receivePo(InboundPoRequest req) {
        log.info("iFlow1 PO received correlationId={} source={} target={}",
                req.getCorrelationId(), req.getSourceSystem(), req.getTargetSystem());

        boolean system2ToSystem1 = "system2".equalsIgnoreCase(req.getSourceSystem())
                && "system1".equalsIgnoreCase(req.getTargetSystem());

        if (system2ToSystem1 && req.getCorrelationId() != null) {
            Order existingMirror = orderRepository.findBySystemIdAndDirectionAndCorrelationId(
                    SystemId.SYSTEM1, Direction.OUTBOUND, req.getCorrelationId());
            if (existingMirror != null) {
                log.info("SYSTEM1 vendor mirror already exists correlationId={} orderId={} status={}",
                        req.getCorrelationId(), existingMirror.getOrderId(), existingMirror.getStatus());
                audit.record("INBOUND", "PO", "iFlow1_PO_Outbound", existingMirror.getOrderId(),
                        req.getCorrelationId(), req.getSourceSystem(), req.getTargetSystem(),
                        null, null, "DEDUP", null);
                return existingMirror;
            }
        }

        // Dedup — survive the System 2 timer flood (write-once on idempotencyKey)
        if (req.getIdempotencyKey() != null) {
            Order existing = orderRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing != null && (!system2ToSystem1
                    || (existing.getSystemId() == SystemId.SYSTEM1
                        && existing.getDirection() == Direction.OUTBOUND))) {
                log.info("Inbound PO dedup hit idempotencyKey={} -> existing {}",
                        req.getIdempotencyKey(), existing.getOrderId());
                audit.record("INBOUND", "PO", "iFlow1_PO_Outbound", existing.getOrderId(),
                        req.getCorrelationId(), req.getSourceSystem(), req.getTargetSystem(),
                        null, null, "DEDUP", null);
                return existing;
            }
        }

        // Open-PO cap — protect against the System 2 auto-raise flood over a long demo
        long openCount = orderRepository.countByPoStatus(PoStatus.RECEIVED);
        if (openCount >= maxOpenPos) {
            log.warn("Open-PO cap reached ({}/{}) — rejecting inbound PO {}",
                    openCount, maxOpenPos, req.getPoNumber());
            audit.record("INBOUND", "PO", "iFlow1_PO_Outbound", req.getPoNumber(),
                    req.getCorrelationId(), req.getSourceSystem(), req.getTargetSystem(),
                    null, null, "REJECTED", "Open-PO cap reached (" + maxOpenPos + ")");
            throw new OpenPoCapExceededException("Open-PO cap reached (" + maxOpenPos + ")");
        }

        double total = req.getTotalAmount() != null ? req.getTotalAmount()
            : (req.getItems() == null ? 0.0 : req.getItems().stream()
                .mapToDouble(i -> (i.getQuantity() == null ? 0 : i.getQuantity())
                                * (i.getUnitPrice() == null ? 0.0 : i.getUnitPrice()))
                .sum());

        if (system2ToSystem1) {
            log.info("Creating SYSTEM1 vendor mirror correlationId={}", req.getCorrelationId());
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
            .orderId(req.getPoNumber() != null ? req.getPoNumber() : generateOrderId())
            .direction(Direction.OUTBOUND)        // inbound PO -> our vendor's approval queue
            .status(OrderStatus.REQUESTED)
            .poStatus(PoStatus.RECEIVED)
            .correlationId(req.getCorrelationId())
            .idempotencyKey(req.getIdempotencyKey() != null
                    ? req.getIdempotencyKey() : req.getPoNumber())
            .sourceSystem(req.getSourceSystem())
            .targetSystem(req.getTargetSystem())
            .systemId(system2ToSystem1
                    ? SystemId.SYSTEM1 : Tenant.fromWireName(req.getTargetSystem()))
            .format(req.getFormat())
            .counterpartyId(system2ToSystem1 ? "S2-PROC" : req.getCounterpartyId())
            .counterpartyName(system2ToSystem1 ? "System 2 Procurement" : req.getCounterpartyName())
            .items(req.getItems())
            .totalAmount(total)
            .stockCheckSent(false)
            .statusUpdatedAt(now)
            .createdAt(now)
            .build();

        Order saved = orderRepository.save(order);
        if (!system2ToSystem1) {
            syncSystem2Mirror(saved);
        }
        log.info("Saved inbound PO mirror orderId={} correlationId={} systemId={} direction={} status={}",
                saved.getOrderId(), saved.getCorrelationId(), saved.getSystemId(),
                saved.getDirection(), saved.getStatus());

        audit.record("INBOUND", "PO", "iFlow1_PO_Outbound", saved.getOrderId(),
                saved.getCorrelationId(), saved.getSourceSystem(), saved.getTargetSystem(),
                null, null, "SUCCESS", null);

        alertService.createOrderAlert(
            "ORDER_RECEIVED",
            "Inbound PO " + saved.getOrderId() + " received via CPI from "
                + (saved.getSourceSystem() == null ? "counterparty" : saved.getSourceSystem()),
            saved.getId(),
            "VENDOR"
        );
        return saved;
    }

    /**
     * A vendor stock decision (OFFER / REJECT) arriving via CPI iFlow 3.
     * Updates the order matched by its CPI correlationId.
     */
    public Order receiveStockOffer(StockOfferRequest req) {
        log.info("CPI stock-offer received correlationId={} decision={} offeredQuantity={}",
                req.getCorrelationId(), req.getDecision(), req.getOfferedQuantity());
        if ("OFFER".equalsIgnoreCase(req.getDecision())
                && (req.getOfferedQuantity() == null || req.getOfferedQuantity() < 0)) {
            throw new IllegalArgumentException("offeredQuantity must be 0 or greater for OFFER");
        }

        Order order = orderRepository.findBySystemIdAndDirectionAndCorrelationId(
                SystemId.SYSTEM2, Direction.INBOUND, req.getCorrelationId());
        if (order == null) {
            Order system1 = orderRepository.findByCorrelationId(req.getCorrelationId());
            if (system1 != null && system1.getDirection() == Direction.OUTBOUND) {
                log.info("Recreating missing SYSTEM2 procurement copy correlationId={} from SYSTEM1 orderId={}",
                        req.getCorrelationId(), system1.getOrderId());
                LocalDateTime now = LocalDateTime.now();
                order = Order.builder()
                    .orderId(system1.getOrderId())
                    .direction(Direction.INBOUND)
                    .status(OrderStatus.REQUESTED)
                    .poStatus(PoStatus.SENT)
                    .correlationId(system1.getCorrelationId())
                    .sourceSystem("system2")
                    .targetSystem("system1")
                    .systemId(SystemId.SYSTEM2)
                    .format(system1.getFormat())
                    .counterpartyId("S1-VENDOR")
                    .counterpartyName("System 1 Vendor")
                    .items(system1.getItems())
                    .totalAmount(system1.getTotalAmount())
                    .expectedDeliveryDate(system1.getExpectedDeliveryDate())
                    .stockCheckSent(false)
                    .statusUpdatedAt(now)
                    .createdAt(now)
                    .build();
                order = orderRepository.save(order);
            } else {
                log.warn("CPI stock-offer order not found correlationId={}", req.getCorrelationId());
                audit.record("INBOUND", "STOCK_OFFER", "iFlow3_Stock_Offer", req.getPoNumber(),
                        req.getCorrelationId(), req.getSourceSystem(), req.getTargetSystem(),
                        null, null, "FAILED", "No order found for correlationId");
                return null;
            }
        }

        OrderStatus oldStatus = order.getStatus();
        log.info("CPI stock-offer order found correlationId={} orderId={} oldStatus={}",
                req.getCorrelationId(), order.getOrderId(), oldStatus);
        if (oldStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order already cancelled");
        }

        if ("OFFER".equalsIgnoreCase(req.getDecision())
                && oldStatus == OrderStatus.STOCK_NOTIFIED
                && req.getOfferedQuantity().equals(order.getAvailableQuantity())) {
            log.info("CPI stock-offer idempotent replay correlationId={} offeredQuantity={}",
                    req.getCorrelationId(), req.getOfferedQuantity());
            return order;
        }

        String alertType;
        String alertMessage;
        if ("REJECT".equalsIgnoreCase(req.getDecision())) {
            order.setStatus(OrderStatus.REJECTED);
            order.setPoStatus(PoStatus.REJECTED);
            order.setCancelledBy("SYSTEM1_VENDOR");
            order.setResolvedAt(LocalDateTime.now());
            alertType = "ORDER_REJECTED";
            alertMessage = "PO " + order.getOrderId() + " rejected by vendor — cannot supply.";
        } else {
            order.setAvailableQuantity(req.getOfferedQuantity() == null ? 0 : req.getOfferedQuantity());
            order.setStockCheckSent(true);
            order.setStatus(OrderStatus.STOCK_NOTIFIED);
            alertType = "STOCK_NOTIFIED";
            alertMessage = "Vendor offered "
                    + (req.getOfferedQuantity() == null ? 0 : req.getOfferedQuantity())
                    + " units for PO " + order.getOrderId() + ".";
        }

        order.setStatusUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        log.info("Stock-offer inbound accepted correlationId={} offeredQuantity={} status={}",
                saved.getCorrelationId(), saved.getAvailableQuantity(), saved.getStatus());
        log.info("CPI stock-offer status updated correlationId={} oldStatus={} newStatus={}",
                saved.getCorrelationId(), oldStatus, saved.getStatus());
        try {
            alertService.createOrderAlert(alertType, alertMessage, saved.getId(), "PROCUREMENT");
        } catch (Exception e) {
            log.warn("Stock-offer alert failed after save correlationId={}: {}",
                    saved.getCorrelationId(), e.getMessage());
        }
        try {
            audit.record("INBOUND", "STOCK_OFFER", "iFlow3_Stock_Offer", saved.getOrderId(),
                    saved.getCorrelationId(), req.getSourceSystem(), req.getTargetSystem(),
                    null, null, "SUCCESS", null);
        } catch (Exception e) {
            log.warn("Stock-offer audit failed after save correlationId={}: {}",
                    saved.getCorrelationId(), e.getMessage());
        }
        log.info("Stock-offer applied orderId={} decision={} offered={}",
                saved.getOrderId(), req.getDecision(), req.getOfferedQuantity());
        return saved;
    }

    /** Approval/rejection coming back for a PO WE raised — matched by correlationId. */
    public Order receiveApproval(ApprovalCallbackRequest req) {
        log.info("CPI approval received correlationId={} poNumber={} decision={} decidedBy={}",
            req.getCorrelationId(), req.getPoNumber(), req.getDecision(), req.getDecidedBy());

        Order vendorMirror = vendorBuyerApprovalService.apply(req);
        if (vendorMirror != null) {
            audit.record("INBOUND", "APPROVAL", "iFlow2_Approval_Callback",
                    vendorMirror.getOrderId(), vendorMirror.getCorrelationId(),
                    vendorMirror.getSourceSystem(), vendorMirror.getTargetSystem(),
                    null, null, "SUCCESS", null);
            return vendorMirror;
        }

        Order order = orderRepository.findByCorrelationId(req.getCorrelationId());
        log.info("CPI approval repository lookup correlationId={} found={} orderId={} status={} poStatus={}",
            req.getCorrelationId(),
            order != null,
            order == null ? null : order.getOrderId(),
            order == null ? null : order.getStatus(),
            order == null ? null : order.getPoStatus());
        if (order == null) {
            log.warn("Approval callback for unknown correlationId={}", req.getCorrelationId());
            audit.record("INBOUND", "APPROVAL", "iFlow2_Approval_Callback", null,
                    req.getCorrelationId(), null, null, null, null,
                    "FAILED", "No PO found for correlationId");
            return null;
        }
        // Existing non-vendor lane remains idempotent.
        if (order.getPoStatus() == PoStatus.APPROVED || order.getPoStatus() == PoStatus.REJECTED) {
            log.info("Approval dedup — {} already {}", order.getOrderId(), order.getPoStatus());
            syncSystem2Mirror(order);
            audit.record("INBOUND", "APPROVAL", "iFlow2_Approval_Callback", order.getOrderId(),
                    req.getCorrelationId(), order.getSourceSystem(), order.getTargetSystem(),
                    null, null, "DEDUP", null);
            return order;
        }

        boolean approved = "APPROVED".equalsIgnoreCase(req.getDecision());
        OrderStatus oldStatus = order.getStatus();
        PoStatus oldPoStatus = order.getPoStatus();
        if (approved) {
            order.setPoStatus(PoStatus.APPROVED);
            order.setStatus(OrderStatus.CONFIRMED);
            order.setCancelledBy(null);
            alertService.createOrderAlert(
                "ORDER_CONFIRMED",
                "PO " + order.getOrderId() + " APPROVED by "
                    + (req.getDecidedBy() == null ? "counterparty" : req.getDecidedBy()),
                order.getId(), alertTargetFor(order));
        } else {
            order.setBuyerResponse("NO");
            order.setPoStatus(PoStatus.REJECTED);
            String cancelledBy = cancellationActorFor(order, req);
            order.setStatus("SYSTEM2_PROCUREMENT".equals(cancelledBy)
                ? OrderStatus.CANCELLED
                : OrderStatus.REJECTED);
            order.setCancelledBy(cancelledBy);
            alertService.createOrderAlert(
                "ORDER_REJECTED",
                "PO " + order.getOrderId() + " REJECTED by "
                    + (req.getDecidedBy() == null ? "counterparty" : req.getDecidedBy())
                    + (req.getReason() != null ? " — " + req.getReason() : ""),
                order.getId(), alertTargetFor(order));
        }
        order.setStatusUpdatedAt(LocalDateTime.now());
        order.setResolvedAt(LocalDateTime.now());
        log.info("CPI approval status transition orderId={} correlationId={} oldStatus={} oldPoStatus={} newStatus={} newPoStatus={}",
            order.getOrderId(), order.getCorrelationId(), oldStatus, oldPoStatus,
            order.getStatus(), order.getPoStatus());
        Order saved = orderRepository.save(order);
        log.info("CPI approval save success orderId={} correlationId={} status={} poStatus={}",
            saved.getOrderId(), saved.getCorrelationId(), saved.getStatus(), saved.getPoStatus());
        syncSystem2Mirror(saved);
        audit.record("INBOUND", "APPROVAL", "iFlow2_Approval_Callback", saved.getOrderId(),
                saved.getCorrelationId(), saved.getSourceSystem(), saved.getTargetSystem(),
                null, null, "SUCCESS", null);
        log.info("Approval applied orderId={} decision={}", saved.getOrderId(), saved.getPoStatus());
        return saved;
    }
}
