package com.supplychain.integration_hub;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "suppliers")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Supplier {
    @Id private String id;
    private String supplierId;
    private SystemId systemId;          // tenant discriminator (SYSTEM1 / SYSTEM2)
    private String supplierCode;
    private String companyName;
    private String email;
    private String password;
    private String role;
    private String contactPersonName;
    private String contactPersonEmail;
    private String contactPersonPhone;
    private String supplierType;
    private String preferredProtocol;
    private String businessCategory;
    private String city;
    private String state;
    private String country;
    private String currency;
    private String gstin;
    private String integrationStatus;
    private String phone;
    private Boolean isActive;
    private Boolean isEmailVerified;
    private Boolean isLocked;
    private Integer failedLoginAttempts;
    private Double rating;
    private LocalDateTime lockedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime onboardedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}