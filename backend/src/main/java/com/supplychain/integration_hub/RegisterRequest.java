package com.supplychain.integration_hub;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String email;
    private String password;
    private String role;        // "ADMIN", "MANAGER", "VENDOR", "PROCUREMENT"
    private String name;
    private String entityId;
    private String entityType;
    private String systemId;    // "SYSTEM1" | "SYSTEM2" (defaults to SYSTEM1 if absent)
}