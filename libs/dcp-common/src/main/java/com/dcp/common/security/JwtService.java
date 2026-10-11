package com.dcp.common.security;

import com.dcp.common.error.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class JwtService {

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public void assertUsableSecret() {
        if (properties.getSecret() == null || properties.getSecret().length() < 32) {
            throw new IllegalStateException("dcp.jwt.secret must contain at least 32 characters");
        }
    }

    public String issueAccessToken(UUID userId, String email, Set<String> roles) {
        assertUsableSecret();
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getAccessTokenMinutes() * 60);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key())
                .compact();
    }

    public JwtPrincipal parse(String token) {
        assertUsableSecret();
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Set<String> roles = new LinkedHashSet<>();
        Object raw = claims.get("roles");
        if (raw instanceof Collection<?> collection) {
            for (Object role : collection) {
                roles.add(String.valueOf(role));
            }
        }
        return new JwtPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class), Set.copyOf(roles));
    }

    public long accessTokenSeconds() {
        return properties.getAccessTokenMinutes() * 60;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public static void rejectDevSecretsInProduction(String[] profiles, String... secrets) {
        boolean prod = false;
        if (profiles != null) {
            for (String profile : profiles) {
                if ("prod".equals(profile)) {
                    prod = true;
                    break;
                }
            }
        }
        if (!prod) {
            return;
        }
        for (String secret : secrets) {
            if (secret == null || secret.startsWith("dev-only-") || secret.length() < 32) {
                throw new IllegalStateException("Refusing to start with a development secret while the prod profile is active");
            }
        }
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    public static UUID requireUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw ApiException.badRequest(field + " must be a UUID");
        }
    }
}
