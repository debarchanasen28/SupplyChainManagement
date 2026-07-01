package com.supplychain.integration_hub;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String name;
    private String role;
    private String entityId;
    private String entityType;
    private Boolean isActive;
    private String password;
}