package com.supplychain.integration_hub;

/**
 * Intentionally inert (not a Spring bean, not scheduled).
 *
 * The order-triggered iFlow4 inventory wiring lives directly at the existing lifecycle points:
 *   - Flow A: {@link OrderService#confirmSupply} (vendor confirms supply) -> VENDOR_SUPPLY / DECREASE
 *   - Flow B: {@link OrderService#advanceToDelivered} for INBOUND orders -> PROCUREMENT_RECEIVE / INCREASE
 * No separate scheduler / collection / retry framework is used.
 */
final class InventoryDeliverySyncScheduler {
    private InventoryDeliverySyncScheduler() { }
}
