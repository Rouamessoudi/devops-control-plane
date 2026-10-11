package com.dcp.auth.service;

import com.dcp.auth.domain.Role;
import com.dcp.auth.domain.User;
import com.dcp.auth.repo.NotificationRepository;
import com.dcp.auth.repo.RefreshTokenRepository;
import com.dcp.auth.repo.UserRepository;
import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private NotificationRepository notifications;

    @Mock
    private AuditService auditService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptyRoleSetIsRejected() {
        UserAdminService service = service();
        authenticate(UUID.randomUUID());

        ApiException exception = assertThrows(ApiException.class, () -> service.updateRoles(UUID.randomUUID(), Set.of(), "127.0.0.1"));

        assertEquals(400, exception.status());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownRoleIsRejected() {
        UserAdminService service = service();
        authenticate(UUID.randomUUID());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRoles(UUID.randomUUID(), Set.of("OWNER"), "127.0.0.1"));

        assertEquals(400, exception.status());
    }

    @Test
    void adminCannotDeleteOwnAccount() {
        UUID actor = UUID.randomUUID();
        authenticate(actor);
        UserAdminService service = service();

        ApiException exception = assertThrows(ApiException.class, () -> service.delete(actor, "127.0.0.1"));

        assertEquals(403, exception.status());
        verify(users, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletingAnotherUserRemovesTokensAndNotifications() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User user = new User();
        user.setId(target);
        user.setEmail("dev@dcp.local");
        user.setFullName("Dev");
        user.replaceRoles(Set.of(Role.DEVELOPER));
        authenticate(actor);
        when(users.findById(target)).thenReturn(Optional.of(user));
        UserAdminService service = service();

        service.delete(target, "127.0.0.1");

        verify(refreshTokens).deleteByUserId(target);
        verify(notifications).deleteByUserId(target);
        verify(users).delete(user);
        verify(auditService).record(actor, AuditService.USER_DELETED, "127.0.0.1", "userId=" + target);
    }

    private UserAdminService service() {
        return new UserAdminService(users, refreshTokens, notifications, auditService);
    }

    private static void authenticate(UUID userId) {
        JwtPrincipal principal = new JwtPrincipal(userId, "admin@dcp.local", Set.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }
}
