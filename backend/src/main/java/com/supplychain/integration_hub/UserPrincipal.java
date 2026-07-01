package com.supplychain.integration_hub;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {

    private String email;
    private String role;
    private String systemId;
}