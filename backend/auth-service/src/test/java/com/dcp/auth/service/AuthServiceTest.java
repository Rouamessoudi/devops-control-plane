package com.dcp.auth.service;

import com.dcp.auth.api.AuthResponse;
import com.dcp.auth.api.LoginRequest;
import com.dcp.auth.api.RefreshRequest;
import com.dcp.auth.api.RegisterRequest;
import com.dcp.auth.api.UserResponse;
import com.dcp.auth.domain.RefreshToken;
import com.dcp.auth.domain.Role;
import com.dcp.auth.domain.User;
import com.dcp.auth.repo.RefreshTokenRepository;
import com.dcp.auth.repo.UserRepository;
import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerEncodesPassword() {
        when(users.existsByEmail("dev@example.com")).thenReturn(false);
        when(passwordEncoder.encode("GoodPassword1")).thenReturn("$2a$10$hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return user;
        });

        UserResponse response = authService.register(
                new RegisterRequest("Dev@Example.com", "GoodPassword1", "Dev User"), "127.0.0.1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(passwordEncoder).encode("GoodPassword1");
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("$2a$10$hash", saved.getPasswordHash());
        assertNotEquals("GoodPassword1", saved.getPasswordHash());
        assertEquals("dev@example.com", saved.getEmail());
        assertEquals(Set.of(Role.DEVELOPER), saved.getRoles());
        assertTrue(saved.isEnabled());
        assertEquals("dev@example.com", response.email());
        assertEquals(Set.of("DEVELOPER"), response.roles());
        assertEquals("Dev User", response.fullName());
        verify(auditService).record(eq(saved.getId()), eq(AuditService.REGISTER), eq("127.0.0.1"), eq("email=dev@example.com"));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("dev@example.com")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> authService.register(
                new RegisterRequest("dev@example.com", "GoodPassword1", "Dev User"), "127.0.0.1"));

        assertEquals(409, exception.status());
        verify(passwordEncoder, never()).encode(anyString());
        verify(users, never()).save(any());
    }

    @Test
    void loginRejectsBadPassword() {
        User user = user(UUID.randomUUID(), "dev@example.com", "hashed");
        when(users.findByEmail("dev@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1", "hashed")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(
                new LoginRequest("dev@example.com", "WrongPass1"), "10.0.0.1"));

        assertEquals(401, exception.status());
        assertFalse(exception.getMessage().contains("WrongPass1"));
        verify(jwtService, never()).issueAccessToken(any(), any(), any());
        verify(refreshTokens, never()).save(any());
        verify(auditService).record(eq(user.getId()), eq(AuditService.LOGIN_FAILURE), eq("10.0.0.1"), eq("email=dev@example.com"));
    }

    @Test
    void refreshRotatesTokenHash() {
        String presented = "a".repeat(64);
        String oldHash = JwtService.sha256Hex(presented);
        UUID userId = UUID.randomUUID();
        User user = user(userId, "dev@example.com", "hashed");
        RefreshToken stored = new RefreshToken();
        stored.setUserId(userId);
        stored.setTokenHash(oldHash);
        stored.setRevoked(false);
        stored.setExpiresAt(Instant.now().plus(Duration.ofDays(2)));

        when(refreshTokens.findByTokenHash(oldHash)).thenReturn(Optional.of(stored));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(eq(userId), eq("dev@example.com"), eq(Set.of("DEVELOPER")))).thenReturn("access-token");
        when(jwtService.accessTokenSeconds()).thenReturn(1234L);
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.refresh(new RefreshRequest(presented));

        assertEquals("access-token", response.accessToken());
        assertEquals(1234L, response.expiresIn());
        assertEquals("Bearer", response.tokenType());
        assertTrue(response.refreshToken().matches("[0-9a-f]{64}"));
        assertTrue(stored.isRevoked());
        assertEquals(oldHash, stored.getTokenHash());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).findByTokenHash(oldHash);
        verify(refreshTokens, never()).findByTokenHash(presented);
        verify(refreshTokens, atLeastOnce()).save(captor.capture());
        RefreshToken created = captor.getAllValues().stream()
                .filter(token -> !token.isRevoked())
                .findFirst()
                .orElseThrow();
        assertEquals(userId, created.getUserId());
        assertEquals(JwtService.sha256Hex(response.refreshToken()), created.getTokenHash());
        assertNotEquals(oldHash, created.getTokenHash());
        assertNotEquals(presented, created.getTokenHash());
        assertNotEquals(response.refreshToken(), created.getTokenHash());
        Instant earliest = Instant.now().plus(AuthService.REFRESH_TTL).minus(Duration.ofMinutes(1));
        Instant latest = Instant.now().plus(AuthService.REFRESH_TTL).plus(Duration.ofMinutes(1));
        assertFalse(created.getExpiresAt().isBefore(earliest));
        assertFalse(created.getExpiresAt().isAfter(latest));
    }

    private static User user(UUID id, String email, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName("Dev User");
        user.setEnabled(true);
        user.setRoles(Set.of(Role.DEVELOPER));
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
