package com.dcp.common.web;

import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.security.JwtProperties;
import com.dcp.common.security.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BearerTokenFilterTest {

    private final JwtService jwtService = jwtService();
    private final BearerTokenFilter filter = new BearerTokenFilter(jwtService);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingHeaderContinuesWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void malformedSchemeIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid token"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void validTokenInstallsTheRole() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, "dev@dcp.local", Set.of("DEVELOPER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        JwtPrincipal principal = (JwtPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(userId, principal.userId());
        assertTrue(principal.hasRole("DEVELOPER"));
    }

    @Test
    void internalPathSkipsBearerParsing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/applications");
        request.addHeader("Authorization", "Bearer not-a-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    private static JwtService jwtService() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("dev-only-jwt-secret-change-me-32b");
        properties.setAccessTokenMinutes(15);
        return new JwtService(properties);
    }
}
