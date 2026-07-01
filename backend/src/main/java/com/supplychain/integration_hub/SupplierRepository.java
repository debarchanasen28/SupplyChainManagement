package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends MongoRepository<Supplier, String> {
    List<Supplier> findBySystemId(SystemId systemId);
    long countBySystemId(SystemId systemId);
    boolean existsByEmail(String email);
    boolean existsBySupplierCode(String supplierCode);
    Optional<Supplier> findByEmail(String email);
}
