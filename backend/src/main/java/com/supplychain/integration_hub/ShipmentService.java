package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final AlertService alertService;
    private final OrderRepository orderRepository;
    private final IntegrationLogService integrationLogService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateShipmentId() {
        return "SHP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String getRole(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority();
    }

    private SystemId getSystemId(Authentication auth) {
        return Tenant.of(auth);
    }

    private static final List<String> ACTIVE_STATUSES =
        List.of("PENDING", "IN_TRANSIT", "OUT_FOR_DELIVERY");

    private static final List<String> PAST_STATUSES =
        List.of("DELIVERED", "CANCELLED");

    // ── Read ─────────────────────────────────────────────────────────────────

    public List<Shipment> getAllShipments(Authentication auth) {
        SystemId systemId = getSystemId(auth);
        String role = getRole(auth);
        if ("VENDOR".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirection(systemId, Direction.OUTBOUND);
        }
        if ("PROCUREMENT".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirection(systemId, Direction.INBOUND);
        }
        return shipmentRepository.findBySystemId(systemId);
    }

    public Page<Shipment> getAllShipments(Authentication auth, Pageable pageable) {
        SystemId systemId = getSystemId(auth);
        String role = getRole(auth);
        if ("VENDOR".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirection(systemId, Direction.OUTBOUND, pageable);
        }
        if ("PROCUREMENT".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirection(systemId, Direction.INBOUND, pageable);
        }
        return shipmentRepository.findBySystemId(systemId, pageable);
    }

    public Shipment getShipmentById(String id, Authentication auth) {
        Shipment shipment = shipmentRepository.findById(id).orElse(null);
        if (shipment == null || shipment.getSystemId() != getSystemId(auth)) return null;
        return shipment;
    }

    public List<Shipment> getActiveShipments(Authentication auth) {
        SystemId systemId = getSystemId(auth);
        String role = getRole(auth);
        if ("VENDOR".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(systemId, Direction.OUTBOUND, ACTIVE_STATUSES);
        }
        if ("PROCUREMENT".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(systemId, Direction.INBOUND, ACTIVE_STATUSES);
        }
        return shipmentRepository.findBySystemIdAndStatusIn(systemId, ACTIVE_STATUSES);
    }

    public Page<Shipment> getActiveShipments(Authentication auth, Pageable pageable) {
        SystemId systemId = getSystemId(auth);
        String role = getRole(auth);
        if ("VENDOR".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(
                    systemId, Direction.OUTBOUND, ACTIVE_STATUSES, pageable);
        }
        if ("PROCUREMENT".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(
                    systemId, Direction.INBOUND, ACTIVE_STATUSES, pageable);
        }
        return shipmentRepository.findBySystemIdAndStatusIn(systemId, ACTIVE_STATUSES, pageable);
    }

    public List<Shipment> getPastShipments(Authentication auth) {
        SystemId systemId = getSystemId(auth);
        String role = getRole(auth);
        if ("VENDOR".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(systemId, Direction.OUTBOUND, PAST_STATUSES);
        }
        if ("PROCUREMENT".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(systemId, Direction.INBOUND, PAST_STATUSES);
        }
        return shipmentRepository.findBySystemIdAndStatusIn(systemId, PAST_STATUSES);
    }

    public Page<Shipment> getPastShipments(Authentication auth, Pageable pageable) {
        SystemId systemId = getSystemId(auth);
        String role = getRole(auth);
        if ("VENDOR".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(
                    systemId, Direction.OUTBOUND, PAST_STATUSES, pageable);
        }
        if ("PROCUREMENT".equals(role)) {
            return shipmentRepository.findBySystemIdAndDirectionAndStatusIn(
                    systemId, Direction.INBOUND, PAST_STATUSES, pageable);
        }
        return shipmentRepository.findBySystemIdAndStatusIn(systemId, PAST_STATUSES, pageable);
    }

    // ── Create ───────────────────────────────────────────────────────────────

    public Shipment createShipment(CreateShipmentRequest req, Authentication auth) {
        String role = getRole(auth);
        Direction direction = "VENDOR".equals(role) ? Direction.OUTBOUND : Direction.INBOUND;

        Shipment shipment = Shipment.builder()
            .shipmentId(generateShipmentId())
            .direction(direction)
            .systemId(getSystemId(auth))
            .orderId(req.getOrderId())
            .counterpartyId(req.getCounterpartyId())
            .counterpartyName(req.getCounterpartyName())
            .carrier(req.getCarrier())
            .trackingNumber(req.getTrackingNumber())
            .origin(req.getOrigin())
            .destination(req.getDestination())
            .status("PENDING")
            .estimatedDelivery(req.getEstimatedDelivery())
            .notes(req.getNotes())
            .build();

        Shipment saved = shipmentRepository.save(shipment);
        Order order = findOrder(saved);
        integrationLogService.logIntegrationEvent(
                order == null ? null : order.getCorrelationId(),
                sourceSystem(saved), targetSystem(saved), "SHIPMENT_CREATED", "SUCCESS",
                "Shipment " + saved.getShipmentId() + " created",
                saved.getOrderId(), 0, null, null);
        return saved;
    }

    // ── Status update ─────────────────────────────────────────────────────────

    public Shipment updateStatus(String id, String newStatus, Authentication auth) {
        Shipment shipment = getShipmentById(id, auth);
        if (shipment == null) return null;

        shipment.setStatus(newStatus);

        if ("DELIVERED".equals(newStatus)) {
            alertService.createOrderAlert(
                "SHIPMENT_DELIVERED",
                "Shipment " + shipment.getShipmentId() + " has been delivered.",
                shipment.getId(),
                shipment.getDirection() == Direction.OUTBOUND ? "VENDOR" : "PROCUREMENT"
            );
        } else if ("IN_TRANSIT".equals(newStatus)) {
            alertService.createOrderAlert(
                "SHIPMENT_IN_TRANSIT",
                "Shipment " + shipment.getShipmentId() + " is now in transit from "
                    + shipment.getOrigin() + " to " + shipment.getDestination() + ".",
                shipment.getId(),
                shipment.getDirection() == Direction.OUTBOUND ? "VENDOR" : "PROCUREMENT"
            );
        }

        Shipment saved = shipmentRepository.save(shipment);
        if ("DELIVERED".equals(newStatus)) {
            Order order = findOrder(saved);
            integrationLogService.logIntegrationEvent(
                    order == null ? null : order.getCorrelationId(),
                    sourceSystem(saved), targetSystem(saved), "DELIVERED", "SUCCESS",
                    "Shipment " + saved.getShipmentId() + " delivered",
                    saved.getOrderId(), 0, null, null);
        }
        return saved;
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    public Shipment cancelShipment(String id, Authentication auth) {
        Shipment shipment = getShipmentById(id, auth);
        if (shipment == null) return null;
        shipment.setStatus("CANCELLED");
        return shipmentRepository.save(shipment);
    }

    private Order findOrder(Shipment shipment) {
        String reference = shipment.getOrderId();
        if (reference == null) return null;
        Order order = orderRepository.findById(reference).orElse(null);
        if (order != null && order.getSystemId() == shipment.getSystemId()) return order;
        return orderRepository.findBySystemIdAndOrderIdAndDirection(
                shipment.getSystemId(), reference, shipment.getDirection());
    }

    private String sourceSystem(Shipment shipment) {
        SystemId tenant = shipment.getSystemId() == null ? SystemId.SYSTEM1 : shipment.getSystemId();
        return shipment.getDirection() == Direction.OUTBOUND
                ? Tenant.wireName(tenant) : Tenant.counterpartyWireName(tenant);
    }

    private String targetSystem(Shipment shipment) {
        SystemId tenant = shipment.getSystemId() == null ? SystemId.SYSTEM1 : shipment.getSystemId();
        return shipment.getDirection() == Direction.OUTBOUND
                ? Tenant.counterpartyWireName(tenant) : Tenant.wireName(tenant);
    }
}
