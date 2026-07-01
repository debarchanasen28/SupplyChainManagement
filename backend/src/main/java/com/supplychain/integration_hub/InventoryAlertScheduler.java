package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the inventory low-stock scan on a fixed interval (default every 10 minutes).
 * Isolated feature — delegates entirely to {@link InventoryAlertService}; touches no existing flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryAlertScheduler {

    private final InventoryAlertService inventoryAlertService;

    @Scheduled(
        fixedDelayString   = "${alert.inventory.scan-interval-ms:600000}",
        initialDelayString = "${alert.inventory.scan-initial-delay-ms:60000}")
    public void runScan() {
        try {
            inventoryAlertService.scanAndAlert();
        } catch (Exception e) {
            log.warn("Inventory alert scan failed: {}", e.getMessage());
        }
    }
}
