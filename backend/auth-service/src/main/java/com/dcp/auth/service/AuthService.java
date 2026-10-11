package com.dcp.auth.service;

import com.dcp.auth.api.AuthResponse;
import com.dcp.auth.api.ChangePasswordRequest;
import com.dcp.auth.api.LoginRequest;
import com.dcp.auth.api.RefreshRequest;
import com.dcp.auth.api.RegisterRequest;
import com.dcp.auth.api.UpdateProfileRequest;
import com.dcp.auth.api.UserResponse;
import com.dcp.auth.domain.RefreshToken;
import com.dcp.auth.domain.Role;
import com.dcp.auth.domain.User;
import com.dcp.auth.repo.RefreshTokenRepository;
import com.dcp.auth.repo.UserRepository;
import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtService;
import com.dcp.common.web.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class AuthService {

    static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final int REFRESH_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(UserRepository users,
                        RefreshTokenRepository refreshTokens,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuditService auditService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request, String ipAddress) {
        PasswordPolicy.requireStrong(request.password());
        String email = normalizeEmail(request.email());
        String fullName = requireFullName(request.fullName());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(fullName);
        user.setEnabled(true);
        user.setRoles(Set.of(Role.DEVELOPER));
        User saved = users.save(user);
        auditService.record(saved.getId(), AuditService.REGISTER, ipAddress, "email=" + email);
        return UserResponse.from(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        String email = normalizeEmail(request.email());
        if (request.password() == null || request.password().isBlank()) {
            throw ApiException.badRequest("password is required");
        }
        User user = users.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(user == null ? null : user.getId(), AuditService.LOGIN_FAILURE, ipAddress, "email=" + email);
            throw ApiException.unauthorized("Invalid credentials");
        }
        if (!user.isEnabled()) {
            auditService.record(user.getId(), AuditService.LOGIN_FAILURE, ipAddress, "account disabled");
            throw ApiException.forbidden("Account is disabled");
        }
        AuthResponse response = issue(user);
        auditService.record(user.getId(), AuditService.LOGIN_SUCCESS, ipAddress, "email=" + email);
        return response;
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw ApiException.badRequest("refreshToken is required");
        }
        String presented = request.refreshToken();
        String oldHash = JwtService.sha256Hex(presented);
        RefreshToken existing = refreshTokens.findByTokenHash(oldHash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (existing.isRevoked() || !existing.getExpiresAt().isAfter(Instant.now())) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        User user = users.findById(existing.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Account is disabled");
        }
        existing.setRevoked(true);
        refreshTokens.save(existing);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(currentUser());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = currentUser();
        user.setFullName(requireFullName(request.fullName()));
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, String ipAddress) {
        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw ApiException.badRequest("currentPassword is required");
        }
        User user = currentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid credentials");
        }
        PasswordPolicy.requireStrong(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        refreshTokens.revokeByUserId(user.getId());
        auditService.record(user.getId(), AuditService.PASSWORD_CHANGED, ipAddress, "updated");
    }

    private AuthResponse issue(User user) {
        String refreshToken = newRefreshToken();
        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(JwtService.sha256Hex(refreshToken));
        stored.setExpiresAt(Instant.now().plus(REFRESH_TTL));
        stored.setRevoked(false);
        refreshTokens.save(stored);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user));
        return new AuthResponse(accessToken, refreshToken, jwtService.accessTokenSeconds(), "Bearer", UserResponse.from(user));
    }

    private User currentUser() {
        UUID userId = CurrentUser.require().userId();
        return users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    static Set<String> roleNames(User user) {
        Set<String> names = new TreeSet<>();
        for (Role role : user.getRoles()) {
            names.add(role.name());
        }
        return Set.copyOf(names);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw ApiException.badRequest("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw ApiException.badRequest("fullName is required");
        }
        String trimmed = fullName.trim();
        if (trimmed.length() > 255) {
            throw ApiException.badRequest("fullName is too long");
        }
        return trimmed;
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
