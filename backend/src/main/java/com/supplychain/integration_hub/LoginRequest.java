package com.supplychain.integration_hub;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}