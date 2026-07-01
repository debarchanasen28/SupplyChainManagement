package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface InventoryAlertRepository extends MongoRepository<InventoryAlert, String> {
    List<InventoryAlert> findBySystemIdOrderByCreatedAtDesc(SystemId systemId);
    Page<InventoryAlert> findBySystemId(SystemId systemId, Pageable pageable);
    Optional<InventoryAlert> findFirstBySystemIdAndSkuAndStatus(
            SystemId systemId, String sku, String status);
}
