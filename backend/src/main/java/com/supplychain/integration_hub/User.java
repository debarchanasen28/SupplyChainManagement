package com.supplychain.integration_hub;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    public enum Role {
        ADMIN,
        MANAGER,
        VENDOR,
        PROCUREMENT
    }

    @Id
    private String id;

    private String userId;          // Readable ID e.g. USR-XXXXXXXX

    @Indexed(unique = true)
    private String email;

    private String password;
    private Role role;
    private String name;

    // Links user to their entity (vendorId, procurementId, etc.)
    private String entityId;
    private String entityType;
    private SystemId systemId;

    // Using Boolean wrapper (not primitive) so Lombok generates getIsActive() / getIsLocked()
    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isLocked = false;

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    private LocalDateTime lockedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime updatedAt;

    @CreatedDate
    private LocalDateTime createdAt;
}