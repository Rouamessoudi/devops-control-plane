package com.dcp.application.service;

import com.dcp.application.api.ApplicationRequest;
import com.dcp.application.api.EnvVarRequest;
import com.dcp.application.api.EnvVarView;
import com.dcp.application.domain.AppEnvVarEntity;
import com.dcp.application.domain.ApplicationEntity;
import com.dcp.application.domain.ApplicationHistoryEntity;
import com.dcp.application.repo.AppEnvVarRepository;
import com.dcp.application.repo.ApplicationHistoryRepository;
import com.dcp.application.repo.ApplicationRepository;
import com.dcp.common.crypto.SecretBox;
import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final String ENCRYPTION_SECRET = "dev-only-env-key-change-me";

    @Mock
    private ApplicationRepository applications;

    @Mock
    private AppEnvVarRepository envVars;

    @Mock
    private ApplicationHistoryRepository history;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creatingAnAppRejectsANonGithubUrl() {
        UUID ownerId = UUID.randomUUID();
        authenticate(ownerId, "USER");
        ApplicationService service = service();
        ApplicationRequest request = new ApplicationRequest(
                "demo",
                "A demo",
                "https://gitlab.com/acme/demo",
                "main",
                "DEV",
                "Dockerfile",
                ".",
                8080);

        ApiException exception = assertThrows(ApiException.class, () -> service.create(request));

        assertEquals(400, exception.status());
        verify(applications, never()).save(any());
    }

    @Test
    void secretEnvVarIsMaskedInUserDto() {
        UUID ownerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(ownerId, "USER");
        SecretBox secretBox = new SecretBox(ENCRYPTION_SECRET);
        ApplicationEntity application = ownedApplication(applicationId, ownerId);
        AppEnvVarEntity secret = envVar(applicationId, "TOKEN", secretBox.encrypt("super-secret"), true);
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));
        when(envVars.findByApplicationIdOrderByEnvKeyAsc(applicationId)).thenReturn(List.of(secret));

        EnvVarView view = service().listEnv(applicationId).get(0);

        assertTrue(view.secret());
        assertEquals("********", view.value());
        assertNotEquals("super-secret", view.value());
        assertNotEquals(secret.getValueCiphertext(), view.value());
    }

    @Test
    void addingASecretDoesNotRecordItsValue() {
        UUID ownerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(ownerId, "USER");
        when(applications.findById(applicationId)).thenReturn(Optional.of(ownedApplication(applicationId, ownerId)));
        when(envVars.findByApplicationIdAndEnvKey(applicationId, "TOKEN")).thenReturn(Optional.empty());
        when(envVars.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(history.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EnvVarView view = service().addEnv(applicationId, new EnvVarRequest("TOKEN", "super-secret", true));

        assertEquals("********", view.value());
        ArgumentCaptor<ApplicationHistoryEntity> captor = ArgumentCaptor.forClass(ApplicationHistoryEntity.class);
        verify(history).save(captor.capture());
        assertFalse(captor.getValue().getSummary().contains("super-secret"));
    }

    @Test
    void nonSecretEnvVarIsReturnedDecrypted() {
        UUID ownerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(ownerId, "USER");
        SecretBox secretBox = new SecretBox(ENCRYPTION_SECRET);
        ApplicationEntity application = ownedApplication(applicationId, ownerId);
        AppEnvVarEntity plain = envVar(applicationId, "LOG_LEVEL", secretBox.encrypt("info"), false);
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));
        when(envVars.findByApplicationIdOrderByEnvKeyAsc(applicationId)).thenReturn(List.of(plain));

        EnvVarView view = service().listEnv(applicationId).get(0);

        assertEquals(false, view.secret());
        assertEquals("info", view.value());
        assertNotEquals(plain.getValueCiphertext(), view.value());
    }

    @Test
    void anotherOwnerGets403() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(otherId, "USER");
        when(applications.findById(applicationId)).thenReturn(Optional.of(ownedApplication(applicationId, ownerId)));

        ApiException exception = assertThrows(ApiException.class, () -> service().get(applicationId));

        assertEquals(403, exception.status());
    }

    private ApplicationService service() {
        return new ApplicationService(applications, envVars, history, new SecretBox(ENCRYPTION_SECRET));
    }

    private static ApplicationEntity ownedApplication(UUID id, UUID ownerId) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(id);
        entity.setOwnerId(ownerId);
        entity.setName("demo");
        return entity;
    }

    private static AppEnvVarEntity envVar(UUID applicationId, String key, String ciphertext, boolean secret) {
        AppEnvVarEntity entity = new AppEnvVarEntity();
        entity.setId(UUID.randomUUID());
        entity.setApplicationId(applicationId);
        entity.setEnvKey(key);
        entity.setValueCiphertext(ciphertext);
        entity.setSecretFlag(secret);
        return entity;
    }

    private static void authenticate(UUID userId, String... roles) {
        JwtPrincipal principal = new JwtPrincipal(userId, userId + "@example.com", Set.of(roles));
        var authorities = java.util.Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
