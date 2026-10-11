package com.dcp.common.web;

import com.dcp.common.security.InternalTokenProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalTokenFilterTest {

    private final InternalTokenFilter filter = new InternalTokenFilter(properties());

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicPathIsNotChecked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/applications/1");
        request.addHeader(InternalTokenFilter.HEADER, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void matchingTokenGrantsInternalRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/applications/1");
        request.addHeader(InternalTokenFilter.HEADER, "dev-only-internal-token-change-me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals("ROLE_INTERNAL", SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
    }

    private static InternalTokenProperties properties() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setInternalToken("dev-only-internal-token-change-me");
        return properties;
    }
}
