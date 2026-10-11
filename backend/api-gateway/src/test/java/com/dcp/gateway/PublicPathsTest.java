package com.dcp.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicPathsTest {

    @Test
    void exposesOnlyAuthenticationAndWebhookPosts() {
        assertTrue(PublicPaths.isPublic("POST", "/api/auth/login"));
        assertTrue(PublicPaths.isPublic("POST", "/api/auth/register"));
        assertTrue(PublicPaths.isPublic("POST", "/api/auth/refresh"));
        assertTrue(PublicPaths.isPublic("POST", "/api/cicd/webhooks/github"));
        assertTrue(PublicPaths.isPublic("GET", "/actuator/health"));
        assertFalse(PublicPaths.isPublic("GET", "/api/auth/me"));
        assertFalse(PublicPaths.isPublic("POST", "/api/applications"));
        assertFalse(PublicPaths.isPublic("GET", "/internal/notifications"));
    }
}
