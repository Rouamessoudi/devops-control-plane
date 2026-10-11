package com.dcp.auth.service;

import com.dcp.auth.api.UserResponse;
import com.dcp.auth.domain.Role;
import com.dcp.auth.domain.User;
import com.dcp.auth.repo.NotificationRepository;
import com.dcp.auth.repo.RefreshTokenRepository;
import com.dcp.auth.repo.UserRepository;
import com.dcp.common.error.ApiException;
import com.dcp.common.web.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final NotificationRepository notifications;
    private final AuditService auditService;

    public UserAdminService(UserRepository users,
                             RefreshTokenRepository refreshTokens,
                             NotificationRepository notifications,
                             AuditService auditService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.notifications = notifications;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllWithRoles().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse updateRoles(UUID userId, Set<String> requested, String ipAddress) {
        Set<Role> roles = parseRoles(requested);
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        user.replaceRoles(roles);
        User saved = users.save(user);
        String roleList = roles.stream().map(Role::name).sorted().collect(Collectors.joining(","));
        auditService.record(CurrentUser.require().userId(), AuditService.ROLE_CHANGED, ipAddress,
                "userId=" + userId + ";roles=" + roleList);
        return UserResponse.from(saved);
    }

    @Transactional
    public void delete(UUID userId, String ipAddress) {
        UUID actorId = CurrentUser.require().userId();
        if (actorId.equals(userId)) {
            throw ApiException.forbidden("You cannot delete your own account");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        refreshTokens.deleteByUserId(userId);
        notifications.deleteByUserId(userId);
        users.delete(user);
        auditService.record(actorId, AuditService.USER_DELETED, ipAddress, "userId=" + userId);
    }

    private static Set<Role> parseRoles(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw ApiException.badRequest("At least one role is required");
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (String requestedRole : requested) {
            if (requestedRole == null || requestedRole.isBlank()) {
                throw ApiException.badRequest("Unsupported role");
            }
            try {
                roles.add(Role.valueOf(requestedRole.trim()));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Unsupported role");
            }
        }
        return roles;
    }
}
