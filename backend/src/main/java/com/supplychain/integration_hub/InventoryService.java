package com.supplychain.integration_hub;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int INITIAL_QUANTITY = 10_000;
    private static final int DEFAULT_THRESHOLD = 1_000;
    private static final String DEFAULT_UNIT = "units";
    private static final String[] SEED_ITEMS = {
        "Raw Iron", "Steel Bolt M8", "Power Connector", "Control Module X1",
        "Copper Wire", "Aluminium Sheet", "Gear Assembly", "Bearing Unit",
        "Hydraulic Pump", "Sensor Module", "Circuit Board", "Packaging Box",
        "Plastic Casing", "Rubber Gasket", "Industrial Valve", "Motor Coil",
        "Battery Pack", "Fastener Kit", "Cable Harness", "Cooling Fan"
    };

    /**
     * Canonical FIXED unit-price list keyed by item name. This is the single source of truth for
     * pricing: the same item/SKU therefore carries the same unitPrice in System1 AND System2.
     * Prices are realistic and constant — never randomly generated. Editing an item's price is a
     * manual operation (InventoryService.updateItem); nothing else mutates it.
     */
    private static final Map<String, Double> FIXED_UNIT_PRICES = Map.ofEntries(
        Map.entry("Raw Iron",          45.00),
        Map.entry("Steel Bolt M8",      2.50),
        Map.entry("Power Connector",   18.75),
        Map.entry("Control Module X1",320.00),
        Map.entry("Copper Wire",       12.40),
        Map.entry("Aluminium Sheet",   85.00),
        Map.entry("Gear Assembly",    145.50),
        Map.entry("Bearing Unit",      65.25),
        Map.entry("Hydraulic Pump",   780.00),
        Map.entry("Sensor Module",    210.00),
        Map.entry("Circuit Board",    175.00),
        Map.entry("Packaging Box",      1.20),
        Map.entry("Plastic Casing",     8.90),
        Map.entry("Rubber Gasket",      3.75),
        Map.entry("Industrial Valve", 240.00),
        Map.entry("Motor Coil",        95.00),
        Map.entry("Battery Pack",     130.00),
        Map.entry("Fastener Kit",      22.00),
        Map.entry("Cable Harness",     56.50),
        Map.entry("Cooling Fan",       34.99)
    );
    private static final double DEFAULT_UNIT_PRICE = 10.00;

    // System2 Vendor stock is a SEPARATE dataset from System1. Same SKUs/prices, but its own
    // quantities. A distinct base quantity makes the separation visible at a glance.
    private static final int SYSTEM2_INITIAL_QUANTITY = 5_000;

    private final InventoryRepository inventoryRepository;
    private final InventoryAlertRepository inventoryAlertRepository;
    private final AlertService alertService;
    private final IntegrationLogService integrationLogService;

    @PostConstruct
    void initializeInventory() {
        seedInventoryIfEmpty();
        backfillFixedPrices();
        seedSystem2InventoryIfEmpty();
    }

    public void seedInventoryIfEmpty() {
        if (inventoryRepository.countBySystemId(SystemId.SYSTEM1) > 0) return;

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < SEED_ITEMS.length; i++) {
            String sku = String.format("SYS1-%03d", i + 1);
            InventoryItem item = InventoryItem.builder()
                .inventoryId("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .systemId(SystemId.SYSTEM1)
                .itemName(SEED_ITEMS[i])
                .sku(sku)
                .quantity(INITIAL_QUANTITY)
                .thresholdQuantity(DEFAULT_THRESHOLD)
                .unit(DEFAULT_UNIT)
                .lastUpdatedAt(now)
                .lastUpdatedBy("SYSTEM_SEED")
                .alertSent(false)
                .unitOfMeasure(DEFAULT_UNIT)
                .reorderLevel(DEFAULT_THRESHOLD)
                .unitPrice(priceFor(SEED_ITEMS[i]))
                .createdAt(now)
                .updatedAt(now)
                .build();
            inventoryRepository.save(item);
        }
        log.info("Seeded {} System1 inventory items with {} units each (fixed prices applied)",
                SEED_ITEMS.length, INITIAL_QUANTITY);
    }

    /**
     * One-time, idempotent migration: stamp the canonical fixed unitPrice onto any inventory record
     * (either system) that predates the pricing feature and is missing a price. Records that already
     * have a price are left untouched so manual edits are never overwritten.
     */
    public void backfillFixedPrices() {
        int updated = 0;
        for (InventoryItem item : inventoryRepository.findAll()) {
            if (item.getUnitPrice() == null) {
                item.setUnitPrice(priceFor(item.getItemName()));
                inventoryRepository.save(item);
                updated++;
            }
        }
        if (updated > 0) log.info("Backfilled fixed unitPrice on {} inventory records", updated);
    }

    /**
     * Seeds the System2 Vendor inventory as its OWN dataset — separate records from System1.
     * It mirrors the System1 catalogue (same SKU + itemName + fixed unitPrice) so the same SKU
     * exists in both systems, but quantities are System2-specific. Inventory records are never
     * shared across systems; only the canonical price is consistent.
     */
    public void seedSystem2InventoryIfEmpty() {
        if (inventoryRepository.countBySystemId(SystemId.SYSTEM2) > 0) return;

        List<InventoryItem> system1 = inventoryRepository.findBySystemId(SystemId.SYSTEM1);
        LocalDateTime now = LocalDateTime.now();
        int seeded = 0;
        for (InventoryItem s1 : system1) {
            InventoryItem item = InventoryItem.builder()
                .inventoryId("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .systemId(SystemId.SYSTEM2)
                .itemName(s1.getItemName())
                .sku(s1.getSku())                       // same SKU as System1 — distinct record
                .category(s1.getCategory())
                .quantity(SYSTEM2_INITIAL_QUANTITY)      // System2-specific quantity
                .thresholdQuantity(DEFAULT_THRESHOLD)
                .unit(s1.getUnit() != null ? s1.getUnit() : DEFAULT_UNIT)
                .lastUpdatedAt(now)
                .lastUpdatedBy("SYSTEM_SEED")
                .alertSent(false)
                .unitOfMeasure(s1.getUnitOfMeasure() != null ? s1.getUnitOfMeasure() : DEFAULT_UNIT)
                .reorderLevel(DEFAULT_THRESHOLD)
                .unitPrice(priceFor(s1.getItemName()))   // same fixed price as System1
                .createdAt(now)
                .updatedAt(now)
                .build();
            inventoryRepository.save(item);
            seeded++;
        }
        log.info("Seeded {} System2 vendor inventory items with {} units each (separate dataset)",
                seeded, SYSTEM2_INITIAL_QUANTITY);
    }

    private double priceFor(String itemName) {
        if (itemName == null) return DEFAULT_UNIT_PRICE;
        return FIXED_UNIT_PRICES.getOrDefault(itemName.trim(), DEFAULT_UNIT_PRICE);
    }

    // ── System2 Vendor inventory: READ-ONLY visibility for System1 Procurement PO creation ──────
    // Returns ONLY System2 records. Never returns System1 stock and never mutates inventory.

    public List<InventoryItem> getSystem2VendorInventory() {
        return inventoryRepository.findBySystemId(SystemId.SYSTEM2);
    }

    /** Look up a System2 vendor inventory item by SKU (used to validate/price Flow-B PO lines). */
    public Optional<InventoryItem> findSystem2ItemBySku(String sku) {
        if (sku == null || sku.isBlank()) return Optional.empty();
        return inventoryRepository.findBySystemIdAndSkuIgnoreCase(SystemId.SYSTEM2, sku.trim());
    }

    /** Read-only snapshot of System1 vendor inventory (used by the System2 procurement generator). */
    public List<InventoryItem> getSystem1VendorInventory() {
        return inventoryRepository.findBySystemId(SystemId.SYSTEM1);
    }

    public List<InventoryItem> getAllInventory() {
        return inventoryRepository.findBySystemId(SystemId.SYSTEM1);
    }

    public InventoryItem increaseStock(String itemName, int quantity, String reason) {
        return increaseStock(itemName, null, quantity, reason, "SYSTEM1_PROCUREMENT");
    }

    public InventoryItem decreaseStock(String itemName, int quantity, String reason) {
        return decreaseStock(itemName, null, quantity, reason, "SYSTEM1_VENDOR");
    }

    /**
     * Apply a CPI iFlow4 inventory movement to System1 stock. Matches the item by sku then itemName.
     *   VENDOR_SUPPLY       -> DECREASE (vendor supplied goods against an approved order)
     *   PROCUREMENT_RECEIVE -> INCREASE (procurement received goods from an external supplier)
     * Returns the updated item. Throws IllegalArgumentException for an unknown eventType,
     * a missing item, or insufficient stock on a decrease.
     */
    public InventoryItem applyInventoryEvent(String eventType, String itemName, String sku,
                                             int quantity, String reason) {
        // [TEMP DEBUG iFlow4-trace] inbound received payload (proves the order-triggered call arrived)
        log.info("[iFlow4-trace] INBOUND received eventType={} itemName={} sku={} quantity={}",
                eventType, itemName, sku, quantity);
        InventoryItem result;
        if ("VENDOR_SUPPLY".equalsIgnoreCase(eventType)) {
            result = decreaseStock(itemName, sku, quantity, reason, "SYSTEM1_VENDOR");
        } else if ("PROCUREMENT_RECEIVE".equalsIgnoreCase(eventType)) {
            result = increaseStock(itemName, sku, quantity, reason, "SYSTEM1_PROCUREMENT");
        } else {
            throw new IllegalArgumentException("Unsupported eventType: " + eventType);
        }
        // [TEMP DEBUG iFlow4-trace] persisted result (proves repository.save executed)
        log.info("[iFlow4-trace] INBOUND saved inventoryId={} itemName={} newQuantity={}",
                result.getId(), result.getItemName(), result.getQuantity());
        return result;
    }

    public InventoryItem receiveStock(InventoryStockRequest request, Authentication auth) {
        requireSystem1(auth);
        requireRole(auth, "PROCUREMENT", "ADMIN", "MANAGER");
        validateRequest(request);
        return increaseStock(request.getItemName(), request.getSku(), request.getQuantity(),
                request.getReason(), auth.getName());
    }

    public InventoryItem sellStock(InventoryStockRequest request, Authentication auth) {
        requireSystem1(auth);
        requireRole(auth, "VENDOR", "ADMIN", "MANAGER");
        validateRequest(request);
        return decreaseStock(request.getItemName(), request.getSku(), request.getQuantity(),
                request.getReason(), auth.getName());
    }

    private InventoryItem increaseStock(String itemName, String sku, int quantity,
                                        String reason, String updatedBy) {
        validateQuantity(quantity);
        InventoryItem item = findItem(itemName, sku);
        int oldQuantity = value(item.getQuantity());
        item.setQuantity(oldQuantity + quantity);
        item.setLastAction("INCREASE");
        item.setLastQuantityChanged(quantity);
        touch(item, updatedBy);
        if (item.getQuantity() > threshold(item)) item.setAlertSent(false);
        InventoryItem saved = inventoryRepository.save(item);
        log.info("System1 inventory receive item={} sku={} oldQuantity={} received={} newQuantity={} reason={} by={}",
                saved.getItemName(), saved.getSku(), oldQuantity, quantity, saved.getQuantity(),
                reason, updatedBy);
        return saved;
    }

    private InventoryItem decreaseStock(String itemName, String sku, int quantity,
                                        String reason, String updatedBy) {
        validateQuantity(quantity);
        InventoryItem item = findItem(itemName, sku);
        int oldQuantity = value(item.getQuantity());
        if (quantity > oldQuantity) {
            throw new IllegalArgumentException("Insufficient stock for " + item.getItemName());
        }
        item.setQuantity(oldQuantity - quantity);
        item.setLastAction("DECREASE");
        item.setLastQuantityChanged(-quantity);
        touch(item, updatedBy);
        InventoryItem saved = inventoryRepository.save(item);
        log.info("System1 inventory sell item={} sku={} oldQuantity={} sold={} newQuantity={} reason={} by={}",
                saved.getItemName(), saved.getSku(), oldQuantity, quantity, saved.getQuantity(),
                reason, updatedBy);
        checkLowStockAlert(saved);
        return saved;
    }

    @Scheduled(fixedDelayString = "${inventory.lowstock-scan-ms:60000}",
               initialDelayString = "${inventory.lowstock-initial-ms:30000}")
    public void lowStockScan() {
        checkLowStockAlert();
    }

    public List<InventoryAlert> checkLowStockAlert() {
        return inventoryRepository.findBySystemId(SystemId.SYSTEM1).stream()
                .map(this::checkLowStockAlert)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private InventoryAlert checkLowStockAlert(InventoryItem item) {
        if (value(item.getQuantity()) > threshold(item)) return null;

        var existing = inventoryAlertRepository.findFirstBySystemIdAndSkuAndStatus(
                SystemId.SYSTEM1, item.getSku(), "OPEN");
        if (item.isAlertSent() || existing.isPresent()) {
            existing.ifPresent(alert -> {
                alert.setCurrentQuantity(value(item.getQuantity()));
                alert.setThresholdQuantity(threshold(item));
                inventoryAlertRepository.save(alert);
            });
            if (!item.isAlertSent()) {
                item.setAlertSent(true);
                inventoryRepository.save(item);
            }
            return existing.orElse(null);
        }

        InventoryAlert alert = InventoryAlert.builder()
                .itemName(item.getItemName())
                .sku(item.getSku())
                .currentQuantity(value(item.getQuantity()))
                .thresholdQuantity(threshold(item))
                .message("Low stock alert: please reorder this item")
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .systemId(SystemId.SYSTEM1)
                .build();
        InventoryAlert savedAlert = inventoryAlertRepository.save(alert);
        item.setAlertSent(true);
        inventoryRepository.save(item);
        log.warn("Created System1 low-stock alert item={} sku={} quantity={} threshold={}",
                item.getItemName(), item.getSku(), item.getQuantity(), threshold(item));
        integrationLogService.logIntegrationEvent(
                null, "system1", "system1", "INVENTORY_ALERT_GENERATED", "SUCCESS",
                "Low stock alert generated for " + item.getItemName());
        return savedAlert;
    }

    public List<InventoryAlert> getInventoryAlerts(Authentication auth) {
        requireSystem1(auth);
        requireRole(auth, "PROCUREMENT", "ADMIN", "MANAGER");
        return inventoryAlertRepository.findBySystemIdOrderByCreatedAtDesc(SystemId.SYSTEM1);
    }

    public Page<InventoryAlert> getInventoryAlerts(Authentication auth, Pageable pageable) {
        requireSystem1(auth);
        requireRole(auth, "PROCUREMENT", "ADMIN", "MANAGER");
        return inventoryAlertRepository.findBySystemId(SystemId.SYSTEM1, pageable);
    }

    public InventoryAlert resolveAlert(String id, Authentication auth) {
        requireSystem1(auth);
        requireRole(auth, "PROCUREMENT", "ADMIN", "MANAGER");
        InventoryAlert alert = inventoryAlertRepository.findById(id).orElse(null);
        if (alert == null || alert.getSystemId() != SystemId.SYSTEM1) return null;
        if (!"RESOLVED".equals(alert.getStatus())) {
            alert.setStatus("RESOLVED");
            alert.setResolvedAt(LocalDateTime.now());
            alert = inventoryAlertRepository.save(alert);
            log.info("Resolved System1 inventory alert id={} sku={} by={}",
                    alert.getId(), alert.getSku(), auth.getName());
        }
        return alert;
    }

    public List<InventoryItem> getAllItems(Authentication auth) {
        requireSystem1(auth);
        return getAllInventory();
    }

    public Page<InventoryItem> getAllItems(Authentication auth, Pageable pageable) {
        requireSystem1(auth);
        return inventoryRepository.findBySystemId(SystemId.SYSTEM1, pageable);
    }

    public InventoryItem getItemById(String id, Authentication auth) {
        requireSystem1(auth);
        InventoryItem item = inventoryRepository.findById(id).orElse(null);
        return item != null && item.getSystemId() == SystemId.SYSTEM1 ? item : null;
    }

    public List<InventoryItem> getLowStockItems(Authentication auth) {
        requireSystem1(auth);
        return getAllInventory().stream()
                .filter(item -> value(item.getQuantity()) <= threshold(item))
                .toList();
    }

    public InventoryItem createItem(CreateInventoryRequest request, Authentication auth) {
        requireSystem1(auth);
        requireRole(auth, "PROCUREMENT", "ADMIN", "MANAGER");
        LocalDateTime now = LocalDateTime.now();
        int threshold = request.getReorderLevel() == null
                ? DEFAULT_THRESHOLD : request.getReorderLevel();
        String unit = request.getUnitOfMeasure() == null
                ? DEFAULT_UNIT : request.getUnitOfMeasure();
        InventoryItem item = InventoryItem.builder()
                .inventoryId("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .systemId(SystemId.SYSTEM1)
                .itemName(request.getItemName())
                .sku(request.getSku())
                .category(request.getCategory())
                .quantity(request.getQuantity() == null ? 0 : request.getQuantity())
                .thresholdQuantity(threshold)
                .unit(unit)
                .lastUpdatedAt(now)
                .lastUpdatedBy(auth.getName())
                .alertSent(false)
                .unitOfMeasure(unit)
                .reorderLevel(threshold)
                .unitPrice(request.getUnitPrice())
                .supplierId(request.getSupplierId())
                .supplierName(request.getSupplierName())
                .warehouseLocation(request.getWarehouseLocation())
                .notes(request.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return inventoryRepository.save(item);
    }

    public InventoryItem updateItem(String id, CreateInventoryRequest request, Authentication auth) {
        requireSystem1(auth);
        InventoryItem item = getItemById(id, auth);
        if (item == null) return null;
        if (request.getItemName() != null) item.setItemName(request.getItemName());
        if (request.getSku() != null) item.setSku(request.getSku());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getQuantity() != null) item.setQuantity(request.getQuantity());
        if (request.getUnitOfMeasure() != null) {
            item.setUnit(request.getUnitOfMeasure());
            item.setUnitOfMeasure(request.getUnitOfMeasure());
        }
        if (request.getReorderLevel() != null) {
            item.setThresholdQuantity(request.getReorderLevel());
            item.setReorderLevel(request.getReorderLevel());
        }
        if (request.getUnitPrice() != null) item.setUnitPrice(request.getUnitPrice());
        if (request.getSupplierId() != null) item.setSupplierId(request.getSupplierId());
        if (request.getSupplierName() != null) item.setSupplierName(request.getSupplierName());
        if (request.getWarehouseLocation() != null) item.setWarehouseLocation(request.getWarehouseLocation());
        if (request.getNotes() != null) item.setNotes(request.getNotes());
        touch(item, auth.getName());
        return inventoryRepository.save(item);
    }

    public InventoryItem updateQuantity(String id, int quantity, Authentication auth) {
        requireSystem1(auth);
        if (quantity < 0) throw new IllegalArgumentException("quantity cannot be negative");
        InventoryItem item = getItemById(id, auth);
        if (item == null) return null;
        item.setQuantity(quantity);
        touch(item, auth.getName());
        if (quantity > threshold(item)) item.setAlertSent(false);
        InventoryItem saved = inventoryRepository.save(item);
        checkLowStockAlert(saved);
        return saved;
    }

    public InventoryItem adjustStock(String id, int delta, Authentication auth) {
        requireSystem1(auth);
        InventoryItem item = getItemById(id, auth);
        if (item == null) return null;
        int quantity = value(item.getQuantity()) + delta;
        if (quantity < 0) throw new IllegalArgumentException("Insufficient stock for " + item.getItemName());
        return updateQuantity(id, quantity, auth);
    }

    public InventoryItem requestRestock(String id, int quantity) {
        InventoryItem item = inventoryRepository.findById(id).orElse(null);
        if (item == null || item.getSystemId() != SystemId.SYSTEM1) return null;
        alertService.createOrderAlert("RESTOCK_REQUEST",
                "Restock requested by vendor: " + quantity + " " + unit(item)
                        + " of " + item.getItemName() + " (current stock " + item.getQuantity() + ").",
                item.getId(), "PROCUREMENT");
        return item;
    }

    public boolean deleteItem(String id, Authentication auth) {
        requireSystem1(auth);
        InventoryItem item = getItemById(id, auth);
        if (item == null) return false;
        inventoryRepository.deleteById(id);
        return true;
    }

    private InventoryItem findItem(String itemName, String sku) {
        if (sku != null && !sku.isBlank()) {
            var bySku = inventoryRepository.findBySystemIdAndSkuIgnoreCase(SystemId.SYSTEM1, sku.trim());
            if (bySku.isPresent()) return bySku.get();
        }
        if (itemName != null && !itemName.isBlank()) {
            var byName = inventoryRepository.findBySystemIdAndItemNameIgnoreCase(
                    SystemId.SYSTEM1, itemName.trim());
            if (byName.isPresent()) return byName.get();
        }
        throw new IllegalArgumentException("Inventory item not found by itemName/sku");
    }

    private void validateRequest(InventoryStockRequest request) {
        if (request == null) throw new IllegalArgumentException("request body is required");
        validateQuantity(request.getQuantity() == null ? 0 : request.getQuantity());
        if ((request.getItemName() == null || request.getItemName().isBlank())
                && (request.getSku() == null || request.getSku().isBlank())) {
            throw new IllegalArgumentException("itemName or sku is required");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than 0");
    }

    private void requireSystem1(Authentication auth) {
        if (auth == null || Tenant.of(auth) != SystemId.SYSTEM1) {
            throw new AccessDeniedException("Inventory management is available only to System1");
        }
    }

    private void requireRole(Authentication auth, String... roles) {
        String role = auth.getAuthorities().iterator().next().getAuthority();
        for (String allowed : roles) {
            if (allowed.equals(role)) return;
        }
        throw new AccessDeniedException("Role " + role + " cannot perform this inventory operation");
    }

    private void touch(InventoryItem item, String updatedBy) {
        LocalDateTime now = LocalDateTime.now();
        item.setLastUpdatedAt(now);
        item.setLastUpdatedBy(updatedBy);
        item.setUpdatedAt(now);
        if (item.getThresholdQuantity() == null) item.setThresholdQuantity(DEFAULT_THRESHOLD);
        if (item.getReorderLevel() == null) item.setReorderLevel(item.getThresholdQuantity());
        if (item.getUnit() == null) item.setUnit(unit(item));
        if (item.getUnitOfMeasure() == null) item.setUnitOfMeasure(item.getUnit());
    }

    private int threshold(InventoryItem item) {
        if (item.getThresholdQuantity() != null) return item.getThresholdQuantity();
        if (item.getReorderLevel() != null) return item.getReorderLevel();
        return DEFAULT_THRESHOLD;
    }

    private String unit(InventoryItem item) {
        if (item.getUnit() != null) return item.getUnit();
        if (item.getUnitOfMeasure() != null) return item.getUnitOfMeasure();
        return DEFAULT_UNIT;
    }

    private int value(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }
}
