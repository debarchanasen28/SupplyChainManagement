package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public List<Supplier> getAllSuppliers(Authentication auth) {
        return supplierRepository.findBySystemId(Tenant.of(auth));
    }

    public Supplier getSupplierById(String id, Authentication auth) {
        Supplier supplier = supplierRepository.findById(id).orElse(null);
        if (supplier == null || supplier.getSystemId() != Tenant.of(auth)) return null;
        return supplier;
    }

    public Supplier createSupplier(CreateSupplierRequest request, Authentication auth) {
        Supplier supplier = Supplier.builder()
            .supplierId("SUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .systemId(Tenant.of(auth))
            .companyName(request.getName())
            .contactPersonName(request.getContactPerson())
            .email(request.getEmail())
            .phone(request.getPhone())
            .businessCategory(request.getCategory())
            .integrationStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE")
            .rating(request.getRating() != null ? request.getRating().doubleValue() : null)
            .isActive(true)
            .isLocked(false)
            .failedLoginAttempts(0)
            .createdAt(LocalDateTime.now())
            .build();
        return supplierRepository.save(supplier);
    }

    public Supplier updateSupplier(String id, UpdateSupplierRequest request, Authentication auth) {
        Supplier supplier = getSupplierById(id, auth);
        if (supplier == null) return null;

        if (request.getName() != null) supplier.setCompanyName(request.getName());
        if (request.getContactPerson() != null) supplier.setContactPersonName(request.getContactPerson());
        if (request.getEmail() != null) supplier.setEmail(request.getEmail());
        if (request.getPhone() != null) supplier.setPhone(request.getPhone());
        if (request.getCategory() != null) supplier.setBusinessCategory(request.getCategory());
        if (request.getStatus() != null) supplier.setIntegrationStatus(request.getStatus());
        if (request.getRating() != null) supplier.setRating(request.getRating().doubleValue());
        supplier.setUpdatedAt(LocalDateTime.now());

        return supplierRepository.save(supplier);
    }

    public Supplier updateStatus(String id, String status, Authentication auth) {
        Supplier supplier = getSupplierById(id, auth);
        if (supplier == null) return null;
        supplier.setIntegrationStatus(status);
        supplier.setUpdatedAt(LocalDateTime.now());
        return supplierRepository.save(supplier);
    }

    public boolean deleteSupplier(String id, Authentication auth) {
        Supplier supplier = getSupplierById(id, auth);
        if (supplier == null) return false;
        supplierRepository.deleteById(id);
        return true;
    }
}
