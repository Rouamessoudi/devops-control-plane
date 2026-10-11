package com.dcp.common.security;

import java.util.Set;
import java.util.UUID;

public record JwtPrincipal(UUID userId, String email, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
