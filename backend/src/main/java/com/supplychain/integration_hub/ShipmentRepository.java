package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ShipmentRepository extends MongoRepository<Shipment, String> {
    List<Shipment> findBySystemId(SystemId systemId);
    Page<Shipment> findBySystemId(SystemId systemId, Pageable pageable);
    List<Shipment> findByDirection(Direction direction);
    List<Shipment> findBySystemIdAndDirection(SystemId systemId, Direction direction);
    Page<Shipment> findBySystemIdAndDirection(SystemId systemId, Direction direction, Pageable pageable);
    List<Shipment> findByStatusIn(List<String> statuses);
    List<Shipment> findBySystemIdAndStatusIn(SystemId systemId, List<String> statuses);
    Page<Shipment> findBySystemIdAndStatusIn(SystemId systemId, List<String> statuses, Pageable pageable);
    List<Shipment> findByDirectionAndStatusIn(Direction direction, List<String> statuses);
    List<Shipment> findBySystemIdAndDirectionAndStatusIn(
        SystemId systemId,
        Direction direction,
        List<String> statuses
    );
    Page<Shipment> findBySystemIdAndDirectionAndStatusIn(
        SystemId systemId,
        Direction direction,
        List<String> statuses,
        Pageable pageable
    );
    List<Shipment> findByDirectionAndStatus(Direction direction, String status);
    List<Shipment> findBySystemIdAndDirectionAndStatus(SystemId systemId, Direction direction, String status);
}
