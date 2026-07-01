package com.supplychain.integration_hub;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Shared, thread-safe holder for order-recovery visibility, written by the two recovery
 * schedulers (Flow A and Flow B) and read by the admin dashboard.
 *
 * Kept flow-agnostic on read (aggregated counts) but updated per-flow so each scheduler
 * owns only its own numbers — the two flows never write each other's fields.
 */
@Component
public class RecoveryMetrics {

    // Flow A — System2 Procurement → System1 Vendor (OUTBOUND).
    private volatile int vendorStuckOrders = 0;
    private volatile int vendorRecoveredLastRun = 0;
    private volatile LocalDateTime vendorLastRunAt;

    // Flow B — System1 Procurement → System2 Vendor (INBOUND).
    private volatile int procurementStuckOrders = 0;
    private volatile int procurementRecoveredLastRun = 0;
    private volatile LocalDateTime procurementLastRunAt;

    public void recordVendorRun(int stuck, int recovered) {
        this.vendorStuckOrders = stuck;
        this.vendorRecoveredLastRun = recovered;
        this.vendorLastRunAt = LocalDateTime.now();
    }

    public void recordProcurementRun(int stuck, int recovered) {
        this.procurementStuckOrders = stuck;
        this.procurementRecoveredLastRun = recovered;
        this.procurementLastRunAt = LocalDateTime.now();
    }

    public int getVendorStuckOrders() {
        return vendorStuckOrders;
    }

    public int getProcurementStuckOrders() {
        return procurementStuckOrders;
    }

    public int getStuckOrdersCount() {
        return vendorStuckOrders + procurementStuckOrders;
    }

    public int getRecoveredOrdersLastRun() {
        return vendorRecoveredLastRun + procurementRecoveredLastRun;
    }

    public LocalDateTime getLastRecoveryRunAt() {
        if (vendorLastRunAt == null) return procurementLastRunAt;
        if (procurementLastRunAt == null) return vendorLastRunAt;
        return vendorLastRunAt.isAfter(procurementLastRunAt) ? vendorLastRunAt : procurementLastRunAt;
    }
}
