package com.dcp.common.security;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void issuesAndParsesAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("dev-only-jwt-secret-change-me-32b");
        properties.setAccessTokenMinutes(15);
        JwtService service = new JwtService(properties);
        UUID userId = UUID.randomUUID();

        String token = service.issueAccessToken(userId, "dev@dcp.local", Set.of("DEVELOPER"));
        JwtPrincipal principal = service.parse(token);

        assertEquals(userId, principal.userId());
        assertEquals("dev@dcp.local", principal.email());
        assertTrue(principal.hasRole("DEVELOPER"));
    }

    @Test
    void rejectsShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("short");
        JwtService service = new JwtService(properties);
        assertThrows(IllegalStateException.class, () -> service.issueAccessToken(UUID.randomUUID(), "a@b.c", Set.of()));
    }

    @Test
    void productionGuardRejectsDevSecret() {
        assertThrows(IllegalStateException.class, () -> JwtService.rejectDevSecretsInProduction(
                new String[]{"prod"}, "dev-only-jwt-secret-change-me-32b"));
    }
}
