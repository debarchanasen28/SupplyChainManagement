package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUserId(String userId);
    Optional<User> findByEmail(String email);
    List<User> findByRole(User.Role role);
    List<User> findByIsActive(Boolean isActive);
    List<User> findByIsLocked(Boolean isLocked);
    Boolean existsByEmail(String email);
}