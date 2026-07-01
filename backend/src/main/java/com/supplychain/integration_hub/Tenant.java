package com.supplychain.integration_hub;

import org.springframework.security.core.Authentication;

/**
 * Resolves the tenant ({@link SystemId}) for the current request and maps between
 * the enum and the lowercase wire names ("system1"/"system2") used on the CPI bridge.
 * Central place for tenant logic so no class hardcodes SYSTEM1.
 */
public final class Tenant {

    private Tenant() {}

    /** Tenant of the authenticated user (from the JWT-backed UserPrincipal); defaults to SYSTEM1. */
    public static SystemId of(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up && up.getSystemId() != null) {
            try {
                return SystemId.valueOf(up.getSystemId());
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return SystemId.SYSTEM1;
    }

    /** Map a wire system name ("system1"/"system2") to a SystemId; defaults to SYSTEM1. */
    public static SystemId fromWireName(String name) {
        return "system2".equalsIgnoreCase(name) ? SystemId.SYSTEM2 : SystemId.SYSTEM1;
    }

    /** Lowercase wire name for a SystemId. */
    public static String wireName(SystemId id) {
        return id == SystemId.SYSTEM2 ? "system2" : "system1";
    }

    /** The counterparty system's wire name. */
    public static String counterpartyWireName(SystemId id) {
        return id == SystemId.SYSTEM2 ? "system1" : "system2";
    }
}
