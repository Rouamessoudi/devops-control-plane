package com.dcp.common.web;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static JwtPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return principal;
    }
}
