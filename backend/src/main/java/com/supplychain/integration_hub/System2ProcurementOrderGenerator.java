package com.supplychain.integration_hub;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FLOW A — System2 Procurement → System1 Vendor.
 *
 * Generates purchase orders that System2 Procurement raises for the System1 Vendor and dispatches
 * them via iFlow1. Each raised PO produces a SYSTEM2 INBOUND record plus a SYSTEM1 OUTBOUND vendor
 * mirror (sourceSystem=system2, targetSystem=system1) that the vendor sees as REQUESTED.
 *
 * This owns Flow-A generation ONLY. It never touches Flow B (System1 Procurement → System2 Vendor).
 * Replaces the previous System2Simulator. Admin controls live in {@link SimulatorController}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class System2ProcurementOrderGenerator {

    private final OrderRepository orderRepository;
    private final CpiClient cpiClient;
    private final CpiAuditService audit;
    private final InventoryService inventoryService;

    @Value("${simulator.enabled:true}")    private boolean enabledOnStart;
    @Value("${simulator.max-open-pos:20}") private long maxOpenPos;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Random rnd = new Random();

    private static final String[] FORMATS = { "json", "xml", "csv" };
    private static final int MAX_ITEMS_PER_PO = 3;   // sometimes more than one item

    @PostConstruct
    void init() {
        running.set(enabledOnStart);
    }

    @Scheduled(fixedDelayString = "${simulator.interval-ms:120000}",
               initialDelayString = "${simulator.initial-delay-ms:20000}")
    public void tick() {
        if (!running.get()) return;
        try {
            raisePoForSystem1Vendor();
        } catch (Exception e) {
            log.warn("Flow-A PO generation failed: {}", e.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setRunning(boolean b) {
        running.set(b);
        log.info("System2 Procurement order generator {}", b ? "STARTED" : "STOPPED");
    }

    public String fireOnce() {
        Order po = raisePoForSystem1Vendor();
        return "raised vendor PO " + (po != null ? po.getOrderId() : "none (cap reached)");
    }

    // ── Admin health visibility (open-PO cap) ──────────────────────────────────
    // The cap is an intentional throttle, NOT an error: when too many open POs exist the generator
    // pauses raising new ones. Surfaced to the admin dashboard as a health indicator.

    public long currentOpenPoCount() {
        return orderRepository.countBySystemIdAndDirectionAndStatus(
                SystemId.SYSTEM2, Direction.INBOUND, OrderStatus.REQUESTED);
    }

    public long getMaxOpenPos() {
        return maxOpenPos;
    }

    public boolean isOpenPoCapReached() {
        return currentOpenPoCount() >= maxOpenPos;
    }

    public Order raisePoForSystem1Vendor() {
        long open = orderRepository.countBySystemIdAndDirectionAndStatus(
                SystemId.SYSTEM2, Direction.INBOUND, OrderStatus.REQUESTED);
        if (open >= maxOpenPos) {
            log.info("Generator: open-PO cap ({}) reached — skipping raise", maxOpenPos);
            return null;
        }

        // Select random items from the SYSTEM1 Vendor inventory master (separate from the System1
        // Procurement dropdown). Uses the fixed inventory unitPrice — never a random price.
        List<InventoryItem> system1Inventory = inventoryService.getSystem1VendorInventory();
        if (system1Inventory.isEmpty()) {
            log.warn("Generator: System1 vendor inventory empty — skipping raise");
            return null;
        }

        List<InventoryItem> shuffled = new ArrayList<>(system1Inventory);
        Collections.shuffle(shuffled, rnd);
        int lineCount = 1 + rnd.nextInt(Math.min(MAX_ITEMS_PER_PO, shuffled.size()));

        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;
        for (int i = 0; i < lineCount; i++) {
            InventoryItem inv = shuffled.get(i);
            int qty = 10 + rnd.nextInt(490);
            double unit = inv.getUnitPrice() == null ? 0.0 : inv.getUnitPrice();  // fixed price
            double lineTotal = qty * unit;
            total += lineTotal;
            items.add(OrderItem.builder()
                    .sku(inv.getSku())
                    .itemName(inv.getItemName())
                    .description(inv.getItemName())
                    .unit(inv.getUnit() != null ? inv.getUnit() : inv.getUnitOfMeasure())
                    .quantity(qty)
                    .unitPrice(unit)
                    .lineTotal(lineTotal)
                    .totalPrice(lineTotal)
                    .build());
        }

        String poNumber = "PO-S2-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String deliveryDate = LocalDate.now().plusDays(7 + rnd.nextInt(14)).toString();
        String format = FORMATS[rnd.nextInt(FORMATS.length)];
        String correlationId = "system2-" + poNumber;

        Order order = Order.builder()
                .orderId(poNumber)
                .direction(Direction.INBOUND)
                .status(OrderStatus.REQUESTED)
                .poStatus(PoStatus.SENT)
                .systemId(SystemId.SYSTEM2)
                .sourceSystem("system2")
                .targetSystem("system1")
                .counterpartyId("S1-VENDOR")
                .counterpartyName("System 1 Vendor")
                .expectedDeliveryDate(deliveryDate)
                .items(items)
                .totalAmount(total)
                .format(format)
                .correlationId(correlationId)
                .statusUpdatedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Order saved = orderRepository.save(order);

        String payload = buildPayload(saved);
        try {
            String resp = cpiClient.sendPo("system2", "system1", correlationId,
                    poNumber, format, payload);
            ensureSystem1VendorMirror(saved);
            audit.record("OUTBOUND", "PO", "iFlow1_PO_Outbound", saved.getOrderId(), correlationId,
                    "system2", "system1", payload, resp, "SUCCESS", null);
            log.info("Generator sent PO {} ({}) to System 1 vendor via iFlow1", saved.getOrderId(), format);
        } catch (Exception e) {
            saved.setPoStatus(PoStatus.FAILED);
            saved.setStatusUpdatedAt(LocalDateTime.now());
            saved = orderRepository.save(saved);
            // Still create the vendor mirror locally so the vendor can act even if iFlow1 is down.
            ensureSystem1VendorMirror(saved);
            audit.record("OUTBOUND", "PO", "iFlow1_PO_Outbound", saved.getOrderId(), correlationId,
                    "system2", "system1", payload, null, "FAILED", e.getMessage());
            log.warn("Generator failed to send PO {} via iFlow1: {}", saved.getOrderId(), e.getMessage());
        }
        return saved;
    }

    private Order ensureSystem1VendorMirror(Order source) {
        Order existing = orderRepository.findBySystemIdAndDirectionAndCorrelationId(
                SystemId.SYSTEM1, Direction.OUTBOUND, source.getCorrelationId());
        if (existing != null) return existing;

        LocalDateTime now = LocalDateTime.now();
        Order mirror = Order.builder()
                .orderId(source.getOrderId())
                .direction(Direction.OUTBOUND)
                .status(OrderStatus.REQUESTED)
                .poStatus(PoStatus.RECEIVED)
                .systemId(SystemId.SYSTEM1)
                .correlationId(source.getCorrelationId())
                .idempotencyKey(source.getOrderId())
                .sourceSystem("system2")
                .targetSystem("system1")
                .counterpartyId("S2-PROC")
                .counterpartyName("System 2 Procurement")
                .expectedDeliveryDate(source.getExpectedDeliveryDate())
                .items(source.getItems())
                .totalAmount(source.getTotalAmount())
                .format(source.getFormat())
                .stockCheckSent(false)
                .statusUpdatedAt(now)
                .createdAt(now)
                .build();
        Order saved = orderRepository.save(mirror);
        log.info("Created SYSTEM1 vendor mirror orderId={} correlationId={}",
                saved.getOrderId(), saved.getCorrelationId());
        return saved;
    }

    private String buildPayload(Order o) {
        String fmt = (o.getFormat() == null) ? "json" : o.getFormat().toLowerCase();
        String[] f = poFields(o);
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
        String productId = (first != null && first.getDescription() != null) ? first.getDescription() : "UNKNOWN";
        String quantity = (first != null && first.getQuantity() != null) ? String.valueOf(first.getQuantity()) : "0";
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
}
