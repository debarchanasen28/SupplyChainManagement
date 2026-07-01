package com.supplychain.integration_hub;

public enum OrderStatus {
    REQUESTED,       // Order request received by VENDOR / PO sent by PROCUREMENT
    STOCK_NOTIFIED,  // VENDOR has stock and has notified buyer — awaiting buyer response
    APPROVED,        // Buyer approved stock offer — awaiting vendor final confirmation
    BUYER_APPROVED,  // System 2 procurement approved — awaiting System 1 vendor confirmation
    BUYER_REJECTED,  // System 2 procurement rejected the vendor stock offer
    VENDOR_REJECTED, // System 1 vendor explicitly rejected the order
    VENDOR_CONFIRMED,// Vendor confirmed supply; retained for shipping-view compatibility
    ACTIVE,          // System 1 vendor finally confirmed — fulfilment lifecycle may advance
    CONFIRMED,       // Buyer confirmed — order locked in
    PROCESSING,      // Being packed / prepared for dispatch
    IN_TRANSIT,      // Physically shipped
    SHIPPED,         // Dispatched to the buyer
    DELIVERED,       // Received by buyer — inventory updated
    REJECTED,        // No stock available
    CANCELLED        // Buyer declined after stock notification / cancelled before shipping
}
