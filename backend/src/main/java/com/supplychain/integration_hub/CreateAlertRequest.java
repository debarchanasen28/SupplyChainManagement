package com.supplychain.integration_hub;

import lombok.Data;

@Data
public class CreateAlertRequest {
    private String title;
    private String message;
    private String severity;
}