package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AlertRepository extends MongoRepository<Alert, String> {
    List<Alert> findBySystemId(SystemId systemId);
    Page<Alert> findBySystemId(SystemId systemId, Pageable pageable);
    List<Alert> findByStatus(String status);
    List<Alert> findBySystemIdAndStatus(SystemId systemId, String status);
    Page<Alert> findBySystemIdAndStatus(SystemId systemId, String status, Pageable pageable);
    List<Alert> findByTargetRole(String targetRole);
    List<Alert> findBySystemIdAndTargetRole(SystemId systemId, String targetRole);
    List<Alert> findByTargetRoleIn(List<String> roles);
    List<Alert> findBySystemIdAndTargetRoleIn(SystemId systemId, List<String> roles);
    Page<Alert> findBySystemIdAndTargetRoleIn(SystemId systemId, List<String> roles, Pageable pageable);
    List<Alert> findByTargetRoleAndStatus(String targetRole, String status);
    List<Alert> findBySystemIdAndTargetRoleAndStatus(SystemId systemId, String targetRole, String status);
    List<Alert> findByTargetRoleInAndStatus(List<String> roles, String status);
    List<Alert> findBySystemIdAndTargetRoleInAndStatus(SystemId systemId, List<String> roles, String status);
    Page<Alert> findBySystemIdAndTargetRoleInAndStatus(
        SystemId systemId, List<String> roles, String status, Pageable pageable);
}
