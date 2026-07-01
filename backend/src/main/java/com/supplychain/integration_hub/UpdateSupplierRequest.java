package com.supplychain.integration_hub;

import lombok.Data;

@Data
public class UpdateSupplierRequest {
    private String name;
    private String contactPerson;
    private String email;
    private String phone;
    private String address;
    private String category;
    private String status;
    private Integer rating;
    private String notes;
}