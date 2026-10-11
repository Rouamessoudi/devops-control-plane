package com.dcp.auth.web;

import com.dcp.auth.api.AuditEventResponse;
import com.dcp.auth.api.AuthResponse;
import com.dcp.auth.api.ChangePasswordRequest;
import com.dcp.auth.api.LoginRequest;
import com.dcp.auth.api.RefreshRequest;
import com.dcp.auth.api.RegisterRequest;
import com.dcp.auth.api.UpdateProfileRequest;
import com.dcp.auth.api.UserResponse;
import com.dcp.auth.service.AuditService;
import com.dcp.auth.service.AuthService;
import com.dcp.common.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, ClientIp.of(http)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientIp.of(http));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        authService.changePassword(request, ClientIp.of(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-events")
    public PageResponse<AuditEventResponse> auditEvents(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return auditService.list(page, size);
    }
}
