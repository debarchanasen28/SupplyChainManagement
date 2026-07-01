package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends MongoRepository<InventoryItem, String> {
    List<InventoryItem> findBySystemId(SystemId systemId);
    Page<InventoryItem> findBySystemId(SystemId systemId, Pageable pageable);
    Optional<InventoryItem> findBySystemIdAndItemNameIgnoreCase(SystemId systemId, String itemName);
    Optional<InventoryItem> findBySystemIdAndSkuIgnoreCase(SystemId systemId, String sku);
    long countBySystemId(SystemId systemId);
    List<InventoryItem> findByCategory(String category);
    List<InventoryItem> findBySystemIdAndCategory(SystemId systemId, String category);
    List<InventoryItem> findBySupplierId(String supplierId);
    List<InventoryItem> findBySystemIdAndSupplierId(SystemId systemId, String supplierId);
}
