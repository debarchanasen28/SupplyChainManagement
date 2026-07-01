package com.supplychain.integration_hub;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private String userId;
    private String name;
    private String entityId;
    private String entityType;
    private String systemId;
}