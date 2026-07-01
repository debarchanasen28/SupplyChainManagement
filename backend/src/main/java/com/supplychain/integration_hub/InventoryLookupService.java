package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Strictly read-only inventory lookup for the vendor order view. Never mutates inventory, never
 * triggers a business process, and is fully independent of the order/approval/CPI/update flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryLookupService {

    private final InventoryRepository inventoryRepository;
    private static final int DEFAULT_THRESHOLD = 1_000;

    /** Look up System1 inventory context by SKU first, then by exact item name, then a fuzzy match. */
    public InventoryContextView lookup(String sku, String itemName) {
        InventoryItem item = null;
        if (sku != null && !sku.isBlank()) {
            item = inventoryRepository.findBySystemIdAndSkuIgnoreCase(SystemId.SYSTEM1, sku.trim()).orElse(null);
        }
        if (item == null && itemName != null && !itemName.isBlank()) {
            item = inventoryRepository.findBySystemIdAndItemNameIgnoreCase(SystemId.SYSTEM1, itemName.trim()).orElse(null);
        }
        // Fallback: tolerate naming differences (e.g. "Copper Wire 10m" vs seed "Copper Wire").
        if (item == null && itemName != null && !itemName.isBlank()) {
            String want = normalize(itemName);
            item = inventoryRepository.findBySystemId(SystemId.SYSTEM1).stream()
                .filter(i -> i.getItemName() != null)
                .filter(i -> {
                    String have = normalize(i.getItemName());
                    return have.equals(want) || have.contains(want) || want.contains(have);
                })
                .findFirst()
                .orElse(null);
        }
        log.info("inventory-context lookup sku={} itemName={} matched={}",
                sku, itemName, item != null ? item.getItemName() : "NONE");
        if (item == null) {
            return InventoryContextView.builder().found(false).itemName(itemName).sku(sku).build();
        }

        int qty = item.getQuantity() == null ? 0 : item.getQuantity();
        int thr = item.getThresholdQuantity() != null ? item.getThresholdQuantity()
                : (item.getReorderLevel() != null ? item.getReorderLevel() : DEFAULT_THRESHOLD);
        double upper = thr * 1.2;

        String status, color;
        if (qty < thr) {
            status = "BELOW_THRESHOLD";       color = "RED";
        } else if (qty <= upper) {
            status = "APPROACHING_THRESHOLD"; color = "YELLOW";
        } else {
            status = "HEALTHY";               color = "GREEN";
        }

        return InventoryContextView.builder()
            .found(true)
            .itemName(item.getItemName())
            .sku(item.getSku())
            .currentQuantity(qty)
            .thresholdQuantity(thr)
            .status(status)
            .statusColor(color)
            .unit(item.getUnit() == null || item.getUnit().isBlank() ? "pcs" : item.getUnit())
            .lastUpdatedAt(item.getLastUpdatedAt())
            .lastAction(item.getLastAction())
            .lastQuantityChanged(item.getLastQuantityChanged())
            .build();
    }

    /** Lowercase + strip everything except letters/digits, so spacing/units/case never block a match. */
    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}

