package com.dcp.common.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

public final class HttpSecuritySupport {

    private HttpSecuritySupport() {
    }

    public static void baseline(HttpSecurity http, BearerTokenFilter bearer, InternalTokenFilter internal) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, ex) -> write(response, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, ex) -> write(response, 403, "Access denied")))
                .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(internal, BearerTokenFilter.class);
    }

    private static void write(HttpServletResponse response, int status, String detail) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"" + (status == 401 ? "Unauthorized" : "Forbidden") + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
