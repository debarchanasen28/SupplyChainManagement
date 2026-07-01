package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final AlertService alertService;
    private final CpiClient cpiClient;
    private final CpiAuditService audit;
    private final InventoryRepository inventoryRepository;
    private final MongoTemplate mongoTemplate;
    private final IntegrationLogService integrationLogService;
    private final System1VendorStockOfferService stockOfferService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String getRole(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority();
    }

    private SystemId getSystemId(Authentication auth) {
        return Tenant.of(auth);   // null-safe; defaults to SYSTEM1
    }

    private String cancellationActor(Authentication auth) {
        String role = getRole(auth);
        if ("VENDOR".equals(role) || "PROCUREMENT".equals(role)) {
            return getSystemId(auth).name() + "_" + role;
        }
        return "SYSTEM_AUTO";
    }

    private List<OrderStatus> system1VendorVisibleStatuses() {
        return List.of(
            OrderStatus.REQUESTED,
            OrderStatus.STOCK_NOTIFIED,
            OrderStatus.BUYER_APPROVED,
            OrderStatus.BUYER_REJECTED,
            OrderStatus.CANCELLED,
            OrderStatus.VENDOR_REJECTED,
            OrderStatus.CONFIRMED,
            OrderStatus.IN_TRANSIT,
            OrderStatus.DELIVERED
        );
    }

    private Order findMirror(Order source, SystemId systemId, Direction direction) {
        if (source.getCorrelationId() != null) {
            Order byCorrelation = orderRepository.findByCorrelationIdAndSystemId(
                source.getCorrelationId(),
                systemId
            );
            if (byCorrelation != null) return byCorrelation;
        }
        return orderRepository.findBySystemIdAndOrderIdAndDirection(
            systemId,
            source.getOrderId(),
            direction
        );
    }

    private Order upsertSystem2VendorMirror(Order source) {
        Order mirror = findMirror(source, SystemId.SYSTEM2, Direction.OUTBOUND);
        if (mirror == null) {
            mirror = Order.builder()
                .orderId(source.getOrderId())
                .direction(Direction.OUTBOUND)
                .systemId(SystemId.SYSTEM2)
                .createdAt(source.getCreatedAt() != null ? source.getCreatedAt() : LocalDateTime.now())
                .build();
        }

        mirror.setStatus(source.getStatus());
        mirror.setPoStatus(source.getPoStatus());
        mirror.setCorrelationId(source.getCorrelationId());
        mirror.setIdempotencyKey(null);
        mirror.setSourceSystem("system1");
        mirror.setTargetSystem("system2");
        mirror.setFormat(source.getFormat());
        mirror.setCounterpartyId("S1-PROC");
        mirror.setCounterpartyName("System 1 Procurement");
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

        return orderRepository.save(mirror);
    }

    private Order upsertSystem2ProcurementMirror(Order source) {
        Order mirror = findMirror(source, SystemId.SYSTEM2, Direction.INBOUND);
        if (mirror == null) {
            mirror = Order.builder()
                .orderId(source.getOrderId())
                .direction(Direction.INBOUND)
                .systemId(SystemId.SYSTEM2)
                .createdAt(source.getCreatedAt() != null ? source.getCreatedAt() : LocalDateTime.now())
                .build();
        }

        mirror.setStatus(source.getStatus());
        mirror.setPoStatus(source.getPoStatus());
        mirror.setCorrelationId(source.getCorrelationId());
        mirror.setIdempotencyKey(null);
        mirror.setSourceSystem("system2");
        mirror.setTargetSystem("system1");
        mirror.setFormat(source.getFormat());
        mirror.setCounterpartyId("S1-VENDOR");
        mirror.setCounterpartyName("System 1 Vendor");
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

        return orderRepository.save(mirror);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    public List<Order> getAllOrders(Authentication auth) {
    String role = getRole(auth);

    if ("VENDOR".equals(role)) {
        List<Order> orders = orderRepository.findBySystemIdAndDirection(
                getSystemId(auth),
                Direction.OUTBOUND
        );
        if (getSystemId(auth) == SystemId.SYSTEM1) {
            List<OrderStatus> visible = system1VendorVisibleStatuses();
            return orders.stream().filter(o -> visible.contains(o.getStatus())).toList();
        }
        return orders;
    }

    if ("PROCUREMENT".equals(role)) {
        return orderRepository.findBySystemIdAndDirection(
                getSystemId(auth),
                Direction.INBOUND
        );
    }

    return orderRepository.findBySystemId(getSystemId(auth));
}

    public Page<Order> getAllOrders(Authentication auth, Pageable pageable) {
        String role = getRole(auth);
        SystemId systemId = getSystemId(auth);
        if ("VENDOR".equals(role)) {
            if (systemId == SystemId.SYSTEM1) {
                return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                        systemId, Direction.OUTBOUND, system1VendorVisibleStatuses(), pageable);
            }
            return orderRepository.findBySystemIdAndDirection(systemId, Direction.OUTBOUND, pageable);
        }
        if ("PROCUREMENT".equals(role)) {
            return orderRepository.findBySystemIdAndDirection(systemId, Direction.INBOUND, pageable);
        }
        return orderRepository.findBySystemId(systemId, pageable);
    }

    // ── Paginated query (tenant + role + tab + status + search + sort) ─────────
    public Map<String, Object> queryOrders(Authentication auth, String tab, String status,
                                           String q, String sort, int page, int size, String direction) {
        if (size <= 0 || size > 100) size = 20;
        if (page < 0) page = 0;

        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("systemId").is(getSystemId(auth)));

        String role = getRole(auth);
        if ("VENDOR".equals(role)) {
            and.add(Criteria.where("direction").is(Direction.OUTBOUND));
            if (getSystemId(auth) == SystemId.SYSTEM1) {
                and.add(Criteria.where("status").in(system1VendorVisibleStatuses()));
            }
        }
        else if ("PROCUREMENT".equals(role)) and.add(Criteria.where("direction").is(Direction.INBOUND));
        else if (direction != null && !direction.isBlank()) {
            // ADMIN/MANAGER can scope to a direction (used by the admin vendor/procurement views)
            try { and.add(Criteria.where("direction").is(Direction.valueOf(direction.trim().toUpperCase()))); }
            catch (IllegalArgumentException ignored) { /* invalid -> no direction filter */ }
        }

        String selectedTab = tab == null ? "all" : tab;
        boolean vendorRole = "VENDOR".equals(role);
        boolean system1Vendor = vendorRole && getSystemId(auth) == SystemId.SYSTEM1;
        if ("active".equals(selectedTab)) {
            List<OrderStatus> activeStatuses;
            if (system1Vendor) {
                // System1 Vendor flow — unchanged.
                activeStatuses = List.of(
                        OrderStatus.CONFIRMED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);
            } else if ("PROCUREMENT".equals(role)) {
                // System1 Procurement → System2 Vendor flow.
                activeStatuses = List.of(
                        OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT,
                        OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            } else {
                activeStatuses = List.of(
                        OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT);
            }
            and.add(Criteria.where("status").in(activeStatuses));
        } else if ("pending".equals(selectedTab)) {
            and.add(Criteria.where("status").in(system1Vendor
                ? List.of(OrderStatus.REQUESTED)
                : List.of(OrderStatus.REQUESTED, OrderStatus.STOCK_NOTIFIED)));
        } else if ("buyerDecision".equals(selectedTab)
                || "buyer-decision".equals(selectedTab)
                || "confirmation".equals(selectedTab)) {
            and.add(Criteria.where("status").is(OrderStatus.BUYER_APPROVED));
        }

        List<OrderStatus> tabStatuses = switch (selectedTab) {
            case "cancelled" -> vendorRole
                ? List.of(OrderStatus.BUYER_REJECTED, OrderStatus.REJECTED,
                          OrderStatus.CANCELLED, OrderStatus.VENDOR_REJECTED)
                : List.of(OrderStatus.REJECTED, OrderStatus.CANCELLED);
            case "delivered" -> List.of(OrderStatus.DELIVERED);
            default          -> List.of();
        };
        if (!tabStatuses.isEmpty()) and.add(Criteria.where("status").in(tabStatuses));

        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try { and.add(Criteria.where("status").is(OrderStatus.valueOf(status))); }
            catch (IllegalArgumentException ignored) { /* unknown status -> no extra filter */ }
        }

        if (q != null && !q.isBlank()) {
            String rx = Pattern.quote(q.trim());
            and.add(new Criteria().orOperator(
                Criteria.where("orderId").regex(rx, "i"),
                Criteria.where("counterpartyName").regex(rx, "i"),
                Criteria.where("status").regex(rx, "i")
            ));
        }

        Query query = new Query();
        if (!and.isEmpty()) query.addCriteria(new Criteria().andOperator(and.toArray(new Criteria[0])));

        Sort sortSpec = switch (sort == null ? "createdAt,desc" : sort) {
            case "dateAsc"   -> Sort.by(Sort.Direction.ASC,  "createdAt");
            case "alphaAsc"  -> Sort.by(Sort.Direction.ASC,  "orderId");
            case "alphaDesc" -> Sort.by(Sort.Direction.DESC, "orderId");
            case "createdAt,asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "orderId,asc" -> Sort.by(Sort.Direction.ASC, "orderId");
            case "orderId,desc" -> Sort.by(Sort.Direction.DESC, "orderId");
            case "status,asc" -> Sort.by(Sort.Direction.ASC, "status");
            case "status,desc" -> Sort.by(Sort.Direction.DESC, "status");
            default          -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        long total = mongoTemplate.count(query, Order.class);
        query.with(sortSpec).skip((long) page * size).limit(size);
        List<Order> content = mongoTemplate.find(query, Order.class);
        log.info("Orders tab query tab={} systemId={} role={} total={} returned={} statusCounts={}",
                selectedTab, getSystemId(auth), role, total, content.size(), statusCounts(content));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("content", content);
        res.put("page", page);
        res.put("size", size);
        res.put("totalElements", total);
        int totalPages = (int) Math.ceil((double) total / size);
        res.put("totalPages", totalPages);
        res.put("first", page == 0);
        res.put("last", totalPages == 0 || page >= totalPages - 1);
        return res;
    }

    public Order getOrderById(String id) {
        return orderRepository.findById(id).orElse(null);
    }

    /**
     * For a vendor viewing an incoming order: how much each line needs vs how much
     * is on hand in inventory — so the vendor doesn't have to check inventory manually.
     * Matches order line description to inventory itemName (case-insensitive).
     */
    public List<Map<String, Object>> getStockAvailability(String id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getItems() == null) return List.of();
        SystemId systemId = order.getSystemId() != null ? order.getSystemId() : SystemId.SYSTEM1;
        List<InventoryItem> inv = inventoryRepository.findBySystemId(systemId);

        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderItem it : order.getItems()) {
            int required = it.getQuantity() == null ? 0 : it.getQuantity();
            Integer available = inv.stream()
                .filter(x -> x.getItemName() != null && it.getDescription() != null
                          && x.getItemName().trim().equalsIgnoreCase(it.getDescription().trim()))
                .map(x -> x.getQuantity() == null ? 0 : x.getQuantity())
                .findFirst().orElse(null);   // null => not tracked in inventory
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("description", it.getDescription());
            line.put("required", required);
            line.put("available", available);
            line.put("sufficient", available != null && available >= required);
            lines.add(line);
        }
        return lines;
    }

  public List<Order> getActiveOrders(Authentication auth) {
    String role = getRole(auth);
    boolean system1Vendor = "VENDOR".equals(role) && getSystemId(auth) == SystemId.SYSTEM1;
    List<OrderStatus> active = system1Vendor
        ? List.of(OrderStatus.CONFIRMED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED)
        : "PROCUREMENT".equals(role)
            ? List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT,
                      OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            : List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT);

    if ("VENDOR".equals(role)) {
        List<Order> result = orderRepository.findBySystemIdAndDirectionAndStatusIn(
                getSystemId(auth),
                Direction.OUTBOUND,
                active
        );
        log.info("Active orders query systemId={} role={} count={}",
                getSystemId(auth), role, result.size());
        return result;
    }

    if ("PROCUREMENT".equals(role))
        return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                getSystemId(auth),
                Direction.INBOUND,
                active
        );

    return orderRepository.findBySystemId(getSystemId(auth))
            .stream()
            .filter(o -> active.contains(o.getStatus()))
            .toList();
}
    public Page<Order> getActiveOrders(Authentication auth, Pageable pageable) {
        String role = getRole(auth);
        SystemId systemId = getSystemId(auth);
        boolean system1Vendor = "VENDOR".equals(role) && systemId == SystemId.SYSTEM1;
        List<OrderStatus> active = system1Vendor
                ? List.of(OrderStatus.CONFIRMED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED)
                : "PROCUREMENT".equals(role)
                    ? List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT,
                              OrderStatus.SHIPPED, OrderStatus.DELIVERED)
                    : List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.IN_TRANSIT);
        if ("VENDOR".equals(role)) {
            return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                    systemId, Direction.OUTBOUND, active, pageable);
        }
        if ("PROCUREMENT".equals(role)) {
            return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                    systemId, Direction.INBOUND, active, pageable);
        }
        return orderRepository.findBySystemIdAndStatusIn(systemId, active, pageable);
    }
    public List<Order> getPastOrders(Authentication auth) {
    List<OrderStatus> past = List.of(OrderStatus.DELIVERED);

    String role = getRole(auth);

    if ("VENDOR".equals(role))
        return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                getSystemId(auth),
                Direction.OUTBOUND,
                past
        );

    if ("PROCUREMENT".equals(role))
        return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                getSystemId(auth),
                Direction.INBOUND,
                past
        );

    return orderRepository.findBySystemId(getSystemId(auth))
            .stream()
            .filter(o -> past.contains(o.getStatus()))
            .toList();
}
    public Page<Order> getPastOrders(Authentication auth, Pageable pageable) {
        String role = getRole(auth);
        if ("VENDOR".equals(role) || "PROCUREMENT".equals(role)) {
            Direction direction = "VENDOR".equals(role) ? Direction.OUTBOUND : Direction.INBOUND;
            return orderRepository.findBySystemIdAndDirectionAndStatusIn(
                    getSystemId(auth), direction, List.of(OrderStatus.DELIVERED), pageable);
        }
        return orderRepository.findBySystemIdAndStatusIn(
                getSystemId(auth), List.of(OrderStatus.DELIVERED), pageable);
    }

    // Pending approval queue — VENDOR sees REQUESTED outbound orders only
    public List<Order> getPendingApprovals(Authentication auth) {
    return orderRepository.findBySystemIdAndDirectionAndStatus(
            getSystemId(auth),
            Direction.OUTBOUND,
            OrderStatus.REQUESTED
    );
}
    public Page<Order> getPendingApprovals(Authentication auth, Pageable pageable) {
        return orderRepository.findBySystemIdAndDirectionAndStatus(
                getSystemId(auth), Direction.OUTBOUND, OrderStatus.REQUESTED, pageable);
    }

    public List<Order> getBuyerDecisions(Authentication auth) {
        List<Order> result = orderRepository.findBySystemIdAndDirectionAndStatusIn(
                getSystemId(auth),
                Direction.OUTBOUND,
                List.of(OrderStatus.BUYER_APPROVED));
        log.info("Buyer Decision orders systemId={} returned={} statusCounts={}",
                getSystemId(auth), result.size(), statusCounts(result));
        return result;
}

    // ── Create ───────────────────────────────────────────────────────────────

    public Order createOrder(CreateOrderRequest req, Authentication auth) {
        String role = getRole(auth);
        Direction direction = "VENDOR".equals(role) ? Direction.OUTBOUND : Direction.INBOUND;

        double total = req.getItems() == null ? 0.0 :
            req.getItems().stream()
                .mapToDouble(i -> i.getQuantity() * i.getUnitPrice())
                .sum();

        Order order = Order.builder()
            .orderId(generateOrderId())
            .direction(direction)
            .status(OrderStatus.REQUESTED)
            .counterpartyId(req.getCounterpartyId())
            .counterpartyName(req.getCounterpartyName())
            .items(req.getItems())
            .totalAmount(total)
            .expectedDeliveryDate(req.getExpectedDeliveryDate())
            .notes(req.getNotes())
            .stockCheckSent(false)
            .statusUpdatedAt(LocalDateTime.now())
            .systemId(getSystemId(auth))
            .createdAt(LocalDateTime.now())
            .build();

        Order saved = orderRepository.save(order);

        if (saved.getSystemId() == SystemId.SYSTEM1 && saved.getDirection() == Direction.INBOUND) {
            // System1 Procurement → System2 Vendor: auto-dispatch via iFlow1 — no manual
            // "Send to Vendor" step. sendOrderToCpi stamps correlationId/sourceSystem/targetSystem,
            // flips poStatus to SENT, and refreshes the System2 Vendor mirror. The
            // System1ProcurementDecisionWatcher then applies the System2 Vendor accept/reject.
            saved = sendOrderToCpi(saved.getId());
        }

        // Notify VENDOR of incoming outbound request
        if (direction == Direction.OUTBOUND) {
            alertService.createOrderAlert(
                "ORDER_RECEIVED",
                "New order request: " + saved.getOrderId() + " from " + saved.getCounterpartyName(),
                saved.getId(),
                "VENDOR"
            );
        }

        return saved;
    }

    public Page<Order> getBuyerDecisions(Authentication auth, Pageable pageable) {
        Page<Order> result = orderRepository.findBySystemIdAndDirectionAndStatusIn(
                getSystemId(auth), Direction.OUTBOUND,
                List.of(OrderStatus.BUYER_APPROVED), pageable);
        log.info("Buyer Decision orders systemId={} total={} returned={} statusCounts={}",
                getSystemId(auth), result.getTotalElements(), result.getNumberOfElements(),
                statusCounts(result.getContent()));
        return result;
    }

    private Map<OrderStatus, Long> statusCounts(List<Order> orders) {
        Map<OrderStatus, Long> counts = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getStatus() != null) {
                counts.merge(order.getStatus(), 1L, Long::sum);
            }
        }
        return counts;
    }

    // ── Outbound to CPI (Phase 1) ────────────────────────────────────────────

    /**
     * Sends an existing order to the counterparty via CPI iFlow 1.
     * Stamps correlationId/idempotencyKey, flips poStatus to SENT, and POSTs the
     * canonical PO. The order is saved as SENT even before this; a CPI failure is
     * logged but does not roll back the order (the UI stays usable).
     */
    public Order sendOrderToCpi(String id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;

        // Idempotency guard — never re-dispatch a PO already sent or decided.
        if (order.getPoStatus() == PoStatus.SENT
                || order.getPoStatus() == PoStatus.APPROVED
                || order.getPoStatus() == PoStatus.REJECTED) {
            log.info("Order {} already {} — skipping re-send", order.getOrderId(), order.getPoStatus());
            return order;
        }

        // Match the correlationId CPI's EnrichPO builds ("<source>-<poNumber>") so the
        // approval callback in Phase 2 reconciles to this exact order.
        // Derive routing from the order's own tenant — no hardcoded system.
        SystemId tenant = order.getSystemId() != null ? order.getSystemId() : SystemId.SYSTEM1;
        String src = Tenant.wireName(tenant);
        String tgt = Tenant.counterpartyWireName(tenant);
        String corr = src + "-" + order.getOrderId();
        order.setCorrelationId(corr);
        order.setIdempotencyKey(order.getOrderId());
        order.setSourceSystem(src);
        order.setTargetSystem(tgt);
        order.setSystemId(tenant);
        if (order.getFormat() == null) order.setFormat("json");
        order.setStatusUpdatedAt(LocalDateTime.now());

        String payload = buildPayload(order);
        try {
            String resp = cpiClient.sendPo(src, tgt, corr, order.getOrderId(),
                    order.getFormat(), payload);
            order.setPoStatus(PoStatus.SENT);
            Order saved = orderRepository.save(order);
            if (saved.getSystemId() == SystemId.SYSTEM1 && saved.getDirection() == Direction.INBOUND) {
                upsertSystem2VendorMirror(saved);
            }
            audit.record("OUTBOUND", "PO", "iFlow1_PO_Outbound", saved.getOrderId(), corr,
                    src, tgt, payload, resp, "SUCCESS", null);
            log.info("Order {} sent to CPI (corrId={})", saved.getOrderId(), corr);
            return saved;
        } catch (Exception e) {
            order.setPoStatus(PoStatus.FAILED);
            Order saved = orderRepository.save(order);
            if (saved.getSystemId() == SystemId.SYSTEM1 && saved.getDirection() == Direction.INBOUND) {
                upsertSystem2VendorMirror(saved);
            }
            audit.record("OUTBOUND", "PO", "iFlow1_PO_Outbound", saved.getOrderId(), corr,
                    src, tgt, payload, null, "FAILED", e.getMessage());
            log.error("Order {} dispatch FAILED (marked FAILED, retryable): {}",
                    saved.getOrderId(), e.getMessage());
            return saved;
        }
    }

    // ── System1 Procurement ← System2 Vendor decision (iFlow2 inbound) ─────────

    /**
     * Applies the System2 Vendor's accept/reject decision to a System1 Procurement PO.
     * Accepted → CONFIRMED; rejected → REJECTED with reason "Rejected by System2 Vendor".
     * Idempotent; invoked by {@link System2VendorDecisionWatcher}. This lane is fully
     * independent of the System2 Procurement buyer-approval flow and never sets BUYER_* states.
     */
    public Order applySystem2VendorDecision(Order order, boolean accepted) {
        if (accepted) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPoStatus(PoStatus.APPROVED);
            order.setCancelledBy(null);
            order.setCancellationReason(null);
        } else {
            order.setStatus(OrderStatus.REJECTED);
            order.setPoStatus(PoStatus.REJECTED);
            order.setCancelledBy("SYSTEM2_VENDOR");
            order.setCancellationReason("Rejected by System2 Vendor");
        }
        order.setBuyerResponse(null);   // Flow A has no buyer decision
        order.setResolvedAt(LocalDateTime.now());
        order.setStatusUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        if (saved.getSystemId() == SystemId.SYSTEM1 && saved.getDirection() == Direction.INBOUND) {
            upsertSystem2VendorMirror(saved);
        }

        try {
            alertService.createOrderAlert(
                accepted ? "ORDER_CONFIRMED" : "ORDER_REJECTED",
                accepted
                    ? "PO " + saved.getOrderId() + " accepted by System 2 Vendor."
                    : "PO " + saved.getOrderId() + " rejected by System 2 Vendor.",
                saved.getId(), "PROCUREMENT");
        } catch (Exception e) {
            log.warn("System2 Vendor decision alert failed orderId={}: {}",
                    saved.getOrderId(), e.getMessage());
        }
        return saved;
    }

    /**
     * Builds the JSON payload in the shape iFlow 1's source schema expects:
     *   { "orders": { "order": { poNumber, supplierId, productId, quantity, deliveryDate } } }
     * Single-product PO — matches the current iFlow 1 mapping. Line items come in Phase 5.
     */
    /** Builds the iFlow 1 payload in the order's requested format (single-product PO; line items = Phase 5). */
    private String buildPayload(Order o) {
        String fmt = (o.getFormat() == null) ? "json" : o.getFormat().toLowerCase();
        String[] f = poFields(o);   // [poNumber, supplierId, productId, quantity, deliveryDate]
        return switch (fmt) {
            case "xml" -> "<orders><order>"
                    + "<poNumber>" + xml(f[0]) + "</poNumber>"
                    + "<supplierId>" + xml(f[1]) + "</supplierId>"
                    + "<productId>" + xml(f[2]) + "</productId>"
                    + "<quantity>" + xml(f[3]) + "</quantity>"
                    + "<deliveryDate>" + xml(f[4]) + "</deliveryDate>"
                    + "</order></orders>";
            case "csv" -> "poNumber,supplierId,productId,quantity,deliveryDate\n"
                    + csv(f[0]) + "," + csv(f[1]) + "," + csv(f[2]) + "," + csv(f[3]) + "," + csv(f[4]);
            default -> "{\"order\":{"
                    + json("poNumber", f[0]) + ","
                    + json("supplierId", f[1]) + ","
                    + json("productId", f[2]) + ","
                    + json("quantity", f[3]) + ","
                    + json("deliveryDate", f[4])
                    + "}}";
        };
    }

    private String[] poFields(Order o) {
        OrderItem first = (o.getItems() != null && !o.getItems().isEmpty()) ? o.getItems().get(0) : null;
        String productId    = (first != null && first.getDescription() != null) ? first.getDescription() : "UNKNOWN";
        String quantity     = (first != null && first.getQuantity() != null) ? String.valueOf(first.getQuantity()) : "0";
        String deliveryDate = o.getExpectedDeliveryDate() != null ? o.getExpectedDeliveryDate() : "";
        return new String[]{ o.getOrderId(), o.getCounterpartyId(), productId, quantity, deliveryDate };
    }

    private String json(String key, String value) {
        return "\"" + key + "\":" + (value == null ? "null"
                : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
    }

    private String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
    // ── Stock-check negotiation (OUTBOUND / VENDOR side) ─────────────────────

    // Step 1: VENDOR checks inventory and notifies buyer of available qty
    public Order notifyStockAvailability(String id, int availableQty) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;

        order.setAvailableQuantity(availableQty);
        order.setStockCheckSent(true);
        order.setStatus(OrderStatus.STOCK_NOTIFIED);
        order.setStatusUpdatedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);

        if (saved.getSystemId() == SystemId.SYSTEM1 && saved.getDirection() == Direction.OUTBOUND) {
            upsertSystem2ProcurementMirror(saved);
        }

        alertService.createOrderAlert(
            "STOCK_NOTIFIED",
            "Stock check sent for order " + saved.getOrderId()
                + ". Available: " + availableQty + " units. Awaiting buyer confirmation.",
            saved.getId(),
            "VENDOR"
        );

        return saved;
    }

    // Step 2: Buyer responds YES or NO (comes in via CPI from simulation)
    public Order respondToStockCheck(String id, String response) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;

        order.setBuyerResponse(response);

        if ("YES".equalsIgnoreCase(response)) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setStatusUpdatedAt(LocalDateTime.now());
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPoStatus(PoStatus.APPROVED);
            order.setCancelledBy(null);
            order.setResolvedAt(LocalDateTime.now());
            order.setStatusUpdatedAt(LocalDateTime.now());
            alertService.createOrderAlert(
                "ORDER_CONFIRMED",
                "Order " + order.getOrderId() + " confirmed by buyer. Preparing for dispatch.",
                order.getId(),
                "VENDOR"
            );
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            order.setPoStatus(PoStatus.REJECTED);
            order.setCancelledBy("SYSTEM2_PROCUREMENT");
            order.setResolvedAt(LocalDateTime.now());
            order.setStatusUpdatedAt(LocalDateTime.now());

            alertService.createOrderAlert(
                "ORDER_CANCELLED",
                "Order " + order.getOrderId() + " was cancelled by System 2 Procurement.",
                order.getId(),
                "VENDOR"
            );
        }

        Order saved = orderRepository.save(order);
        if (saved.getSystemId() == SystemId.SYSTEM1 && saved.getDirection() == Direction.OUTBOUND) {
            upsertSystem2ProcurementMirror(saved);
        }
        return saved;
    }

    // ── Vendor stock decision over CPI iFlow 3 (Lane A) ──────────────────────

    /** Sum of required quantity across all line items. */
    private int totalRequired(Order order) {
        if (order.getItems() == null) return 0;
        return order.getItems().stream()
            .mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity())
            .sum();
    }

    /** Auto-computed offer = sum of min(on-hand, required) per line for the order's tenant. */
    private int computeOfferQuantity(Order order) {
        if (order.getItems() == null) return 0;
        SystemId sid = order.getSystemId() != null ? order.getSystemId() : SystemId.SYSTEM1;
        List<InventoryItem> inv = inventoryRepository.findBySystemId(sid);
        int offered = 0;
        for (OrderItem it : order.getItems()) {
            int req = it.getQuantity() == null ? 0 : it.getQuantity();
            int onHand = inv.stream()
                .filter(x -> x.getItemName() != null && it.getDescription() != null
                          && x.getItemName().trim().equalsIgnoreCase(it.getDescription().trim()))
                .map(x -> x.getQuantity() == null ? 0 : x.getQuantity())
                .findFirst().orElse(0);
            offered += Math.min(onHand, req);
        }
        return offered;
    }

    /**
     * VENDOR sends a stock offer (auto qty = min on-hand, required) to the counterparty
     * procurement over CPI iFlow 3. Locally flips the order to STOCK_NOTIFIED.
     */
    public Order sendVendorStockOffer(String id) {
        // Delegated to System1VendorStockOfferService: marks STOCK_NOTIFIED and sends via CPI iFlow3.
        return stockOfferService.offerStock(id);
    }

    /** VENDOR sends a caller-selected partial/full offer via CPI iFlow3. */
    public Order sendVendorStockOffer(String id, int offeredQuantity, String note) {
        return stockOfferService.offerStock(id, offeredQuantity, note);
    }

    /** VENDOR rejects the PO (cannot supply); reject is sent to procurement via CPI iFlow3. */
    public Order rejectByVendor(String id) {
        return stockOfferService.reject(id);
    }

    /** Simple vendor rejection from the pending-orders UI; no CPI notification. */
    public Order rejectSystem1VendorOrder(String reference) {
        Order order = findSystem1VendorOrder(reference);
        if (order == null) return null;

        order.setStatus(OrderStatus.VENDOR_REJECTED);
        order.setPoStatus(PoStatus.REJECTED);
        order.setCancelledBy("SYSTEM1_VENDOR");
        order.setVendorDecision("REJECTED");
        order.setRejectionReason("Rejected by vendor");
        order.setResolvedAt(LocalDateTime.now());
        order.setStatusUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    /** Simple vendor cancellation from the pending-orders UI; no CPI notification. */
    public Order cancelSystem1VendorOrder(String reference) {
        Order order = findSystem1VendorOrder(reference);
        if (order == null) return null;

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledBy("SYSTEM1_VENDOR");
        order.setCancellationReason("Cancelled by vendor");
        order.setResolvedAt(LocalDateTime.now());
        order.setStatusUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    private Order findSystem1VendorOrder(String reference) {
        Order order = orderRepository.findById(reference).orElse(null);
        if (order == null) {
            order = orderRepository.findBySystemIdAndOrderIdAndDirection(
                    SystemId.SYSTEM1, reference, Direction.OUTBOUND);
        }
        if (order == null) {
            order = orderRepository.findBySystemIdAndDirectionAndCorrelationId(
                    SystemId.SYSTEM1, Direction.OUTBOUND, reference);
        }
        if (order != null
                && (order.getSystemId() != SystemId.SYSTEM1
                    || order.getDirection() != Direction.OUTBOUND)) {
            throw new IllegalStateException("Only a System 1 vendor mirror order can be changed here");
        }
        return order;
    }

    /** VENDOR's final confirmation after the buyer accepted the offer — starts fulfilment. */
    public Order confirmSupply(String id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;
        if (order.getSystemId() != SystemId.SYSTEM1
                || order.getDirection() != Direction.OUTBOUND
                || order.getStatus() != OrderStatus.BUYER_APPROVED
                || !"YES".equalsIgnoreCase(order.getBuyerResponse())) {
            throw new IllegalStateException(
                    "Supply can only be confirmed for a buyer-approved System 1 vendor order");
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setPoStatus(PoStatus.CONFIRMED);
        order.setVendorFinalDecision("CONFIRMED");
        order.setCancelledBy(null);
        order.setStatusUpdatedAt(LocalDateTime.now());
        order.setResolvedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        log.info("Vendor final confirm moved BUYER_APPROVED to CONFIRMED orderId={} correlationId={}",
                saved.getOrderId(), saved.getCorrelationId());

        alertService.createOrderAlert(
            "ORDER_CONFIRMED",
            "Order " + saved.getOrderId() + " confirmed for supply.",
            saved.getId(), "VENDOR");

        // Flow A — vendor supplied goods: DECREASE System1 inventory via iFlow4 (after the existing
        // successful CONFIRMED transition; an iFlow4 failure must not break the confirmation).
        emitInventoryUpdate(saved, "VENDOR_SUPPLY", "System1 vendor confirmed supply");
        return saved;
    }

    public Order cancelBySystem1Vendor(String id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;
        if (order.getSystemId() != SystemId.SYSTEM1 || order.getDirection() != Direction.OUTBOUND) {
            throw new IllegalStateException("Only a System 1 vendor order can be cancelled here");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledBy("SYSTEM1_VENDOR");
        order.setCancellationReason("Cancelled by vendor");
        order.setStatusUpdatedAt(LocalDateTime.now());
        order.setResolvedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        alertService.createOrderAlert(
            "ORDER_CANCELLED",
            "Order " + saved.getOrderId() + " has been cancelled by System 1 Vendor.",
            saved.getId(), "VENDOR");
        return saved;
    }

    // ── Lifecycle transitions (called by OrderLifecycleScheduler) ─────────────

    public void advanceToProcessing(Order order) {
        order.setStatus(OrderStatus.PROCESSING);
        order.setStatusUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        order.setResolvedAt(LocalDateTime.now());
    }

    public void advanceToInTransit(Order order) {
        order.setStatus(OrderStatus.IN_TRANSIT);
        order.setStatusUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        String target = order.getDirection() == Direction.OUTBOUND ? "VENDOR" : "PROCUREMENT";
        alertService.createOrderAlert(
            "ORDER_SHIPPED",
            "Order " + order.getOrderId() + " is now in transit.",
            order.getId(),
            target
        );
    }

    public void advanceToDelivered(Order order) {
        order.setStatus(OrderStatus.DELIVERED);
        order.setStatusUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        String target = order.getDirection() == Direction.OUTBOUND ? "VENDOR" : "PROCUREMENT";
        alertService.createOrderAlert(
            "ORDER_DELIVERED",
            "Order " + order.getOrderId() + " has been delivered successfully.",
            order.getId(),
            target
        );
        integrationLogService.logIntegrationEvent(
                order.getCorrelationId(), order.getSourceSystem(), order.getTargetSystem(),
                "DELIVERED", "SUCCESS", "Order " + order.getOrderId() + " delivered",
                order.getOrderId(), 0, null, null);

        // [TEMP DEBUG iFlow4-trace] prove advanceToDelivered runs and which branch is taken
        log.info("[iFlow4-trace] advanceToDelivered orderId={} systemId={} direction={} willEmit={}",
                order.getOrderId(), order.getSystemId(), order.getDirection(),
                order.getDirection() == Direction.INBOUND);

        // Flow B — System1 procurement received goods (INBOUND delivery): INCREASE System1 inventory
        // via iFlow4 (after the existing DELIVERED transition). OUTBOUND deliveries are Flow A and were
        // already handled at confirmSupply, so they are skipped here.
        if (order.getDirection() == Direction.INBOUND) {
            emitInventoryUpdate(order, "PROCUREMENT_RECEIVE", "System1 procurement received goods");
        }
    }

    /**
     * Wires an existing order action to the working iFlow4 inventory flow. Sends one iFlow4 call per
     * line item (sourceSystem/targetSystem = system1). An iFlow4 failure is logged and swallowed so it
     * never breaks the order action. Inventory is changed only by iFlow4, never directly here.
     */
    private void emitInventoryUpdate(Order order, String eventType, String reason) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            // [TEMP DEBUG iFlow4-trace] prove why nothing was emitted
            log.warn("[iFlow4-trace] emit SKIPPED-NO-ITEMS orderId={} eventType={} itemsNull={}",
                    order == null ? null : order.getOrderId(), eventType,
                    order == null || order.getItems() == null);
            return;
        }
        log.info("iFlow4 inventory update triggered orderId={} correlationId={} eventType={} items={}",
                order.getOrderId(), order.getCorrelationId(), eventType, order.getItems().size());
        for (OrderItem it : order.getItems()) {
            int qty = it.getQuantity() == null ? 0 : it.getQuantity();
            // [TEMP DEBUG iFlow4-trace] per-item, before any skip decision
            log.info("[iFlow4-trace] item orderId={} itemName={} qty={} eventType={}",
                    order.getOrderId(), it.getDescription(), qty, eventType);
            if (qty <= 0 || it.getDescription() == null || it.getDescription().isBlank()) {
                log.warn("[iFlow4-trace] item SKIPPED orderId={} itemName={} qty={} reason={}",
                        order.getOrderId(), it.getDescription(), qty,
                        qty <= 0 ? "quantity<=0" : "blank-itemName");
                continue;
            }
            try {
                cpiClient.sendInventoryUpdate(
                        order.getCorrelationId(), order.getOrderId(), "system1", "system1",
                        eventType, null, it.getDescription(), qty, "pcs", reason);
                log.info("iFlow4 inventory update OK orderId={} item={} qty={} eventType={}",
                        order.getOrderId(), it.getDescription(), qty, eventType);
            } catch (Exception e) {
                log.warn("iFlow4 inventory update failed orderId={} item={} qty={} eventType={}: {}",
                        order.getOrderId(), it.getDescription(), qty, eventType, e.getMessage());
            }
        }
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    public Order cancelOrder(String id, Authentication auth) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return null;

        order.setStatus(OrderStatus.CANCELLED);
        String actor = cancellationActor(auth);
        order.setCancelledBy(actor);
        if ("SYSTEM1_VENDOR".equals(actor)) {
            order.setCancellationReason("Cancelled by vendor");
        }
        order.setStatusUpdatedAt(LocalDateTime.now());
        order.setResolvedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        String target = saved.getDirection() == Direction.OUTBOUND ? "VENDOR" : "PROCUREMENT";
        alertService.createOrderAlert(
            "ORDER_CANCELLED",
            "Order " + saved.getOrderId() + " has been cancelled.",
            saved.getId(),
            target
        );

        return saved;
    }

    // ── For scheduler ────────────────────────────────────────────────────────

    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }
}
