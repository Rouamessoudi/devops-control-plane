package com.dcp.deployment.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.InternalRestClient;
import com.dcp.deployment.api.CreateDeploymentRequest;
import com.dcp.deployment.client.BuildAccepted;
import com.dcp.deployment.client.BuildView;
import com.dcp.deployment.client.K8sApplyRequest;
import com.dcp.deployment.client.K8sDeploymentView;
import com.dcp.deployment.client.RemoteApplication;
import com.dcp.deployment.client.RemoteEnvVar;
import com.dcp.deployment.domain.DeploymentEntity;
import com.dcp.deployment.domain.DeploymentStatus;
import com.dcp.deployment.domain.TriggerType;
import com.dcp.deployment.repo.DeploymentEventRepository;
import com.dcp.deployment.repo.DeploymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deployments;

    @Mock
    private DeploymentEventRepository events;

    @Mock
    private InternalRestClient applicationClient;

    @Mock
    private InternalRestClient dockerClient;

    @Mock
    private InternalRestClient kubernetesClient;

    @Mock
    private InternalRestClient authClient;

    @Mock
    private InternalRestClient monitoringClient;

    private DeploymentService service;

    @BeforeEach
    void setUp() {
        service = new DeploymentService(
                deployments,
                events,
                applicationClient,
                dockerClient,
                kubernetesClient,
                authClient,
                monitoringClient,
                task -> {
                },
                5);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rollbackWithoutPreviousSuccessThrowsConflict() {
        UUID ownerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        authenticate(ownerId, "USER");
        DeploymentEntity current = deployment(deploymentId, applicationId, ownerId);
        current.setStatus(DeploymentStatus.SUCCEEDED);
        current.setImageRepository("ghcr.io/acme/demo");
        current.setImageTag("v2");
        current.setCreatedAt(Instant.parse("2026-04-02T00:00:00Z"));
        DeploymentEntity sameImage = deployment(UUID.randomUUID(), applicationId, ownerId);
        sameImage.setStatus(DeploymentStatus.SUCCEEDED);
        sameImage.setImageRepository("ghcr.io/acme/demo");
        sameImage.setImageTag("v2");
        sameImage.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        when(deployments.findById(deploymentId)).thenReturn(Optional.of(current));
        when(deployments.findByApplicationIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(applicationId), eq(DeploymentStatus.SUCCEEDED), any())).thenReturn(List.of(sameImage));

        ApiException exception = assertThrows(ApiException.class, () -> service.rollback(deploymentId));

        assertEquals(409, exception.status());
        verifyNoInteractions(applicationClient);
    }

    @Test
    void replicasAboveMaxRejected() {
        ApiException exception = assertThrows(ApiException.class, () -> service.createForUser(
                new CreateDeploymentRequest(UUID.randomUUID(), "v1", 6, null)));

        assertEquals(400, exception.status());
        assertTrue(exception.getMessage().contains("5"));
        verifyNoInteractions(applicationClient);
    }

    @Test
    void failurePathSetsFailed() {
        UUID deploymentId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        DeploymentEntity entity = deployment(deploymentId, applicationId, UUID.randomUUID());
        entity.setReplicas(1);
        entity.setTriggerType(TriggerType.MANUAL);
        entity.setStatus(DeploymentStatus.PENDING);
        when(deployments.findById(deploymentId)).thenReturn(Optional.of(entity));
        when(applicationClient.get(eq("/internal/applications/" + applicationId), eq(RemoteApplication.class)))
                .thenReturn(application(applicationId));
        String reason = "x".repeat(1200);
        doThrow(new IllegalStateException(reason)).when(dockerClient)
                .post(eq("/internal/docker/builds"), any(), eq(BuildAccepted.class));

        assertDoesNotThrow(() -> service.runWorkflow(deploymentId, true));

        assertEquals(DeploymentStatus.FAILED, entity.getStatus());
        assertEquals(1000, entity.getErrorMessage().length());
        assertEquals(reason.substring(0, 1000), entity.getErrorMessage());
    }

    @Test
    void downstreamNotificationFailureDoesNotFailDeployment() {
        UUID deploymentId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID buildId = UUID.randomUUID();
        DeploymentEntity entity = deployment(deploymentId, applicationId, UUID.randomUUID());
        entity.setReplicas(1);
        entity.setTriggerType(TriggerType.MANUAL);
        entity.setStatus(DeploymentStatus.PENDING);
        when(deployments.findById(deploymentId)).thenReturn(Optional.of(entity));
        when(applicationClient.get(eq("/internal/applications/" + applicationId), eq(RemoteApplication.class)))
                .thenReturn(application(applicationId));
        when(applicationClient.get(eq("/internal/applications/" + applicationId + "/env"), eq(RemoteEnvVar[].class)))
                .thenReturn(new RemoteEnvVar[]{new RemoteEnvVar("LOG_LEVEL", "info", false)});
        when(dockerClient.post(eq("/internal/docker/builds"), any(), eq(BuildAccepted.class)))
                .thenReturn(new BuildAccepted(buildId, "RUNNING", "ghcr.io/acme/demo", "20260101120000", "ghcr.io/acme/demo:20260101120000"));
        when(dockerClient.get(eq("/internal/docker/builds/" + buildId), eq(BuildView.class)))
                .thenReturn(new BuildView(buildId, applicationId, "SUCCEEDED", "ghcr.io/acme/demo", "20260101120000",
                        "ghcr.io/acme/demo:20260101120000", "", null, null, null, List.of()));
        when(kubernetesClient.post(eq("/internal/kubernetes/deployments"), any(), eq(K8sDeploymentView.class)))
                .thenReturn(new K8sDeploymentView("dcp-demo", "dcp", "ghcr.io/acme/demo:20260101120000", 1, 1, 4L, "RUNNING"));
        doThrow(new IllegalStateException("monitor down")).when(monitoringClient)
                .post(eq("/internal/monitoring/targets"), any());
        doThrow(new IllegalStateException("notify down")).when(authClient)
                .post(eq("/internal/notifications"), any());

        assertDoesNotThrow(() -> service.runWorkflow(deploymentId, true));

        assertEquals(DeploymentStatus.SUCCEEDED, entity.getStatus());
        assertEquals("ghcr.io/acme/demo", entity.getImageRepository());
        assertEquals("20260101120000", entity.getImageTag());
        assertEquals(4L, entity.getK8sRevision());
        assertNull(entity.getErrorMessage());
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(kubernetesClient).post(eq("/internal/kubernetes/deployments"), body.capture(), eq(K8sDeploymentView.class));
        K8sApplyRequest apply = (K8sApplyRequest) body.getValue();
        assertEquals("dcp-demo", apply.name());
        assertEquals("ghcr.io/acme/demo:20260101120000", apply.image());
        assertEquals("info", apply.env().get(0).value());
    }

    @Test
    void rollbackWorkflowSkipsDockerAndReusesPreviousImage() {
        UUID deploymentId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        DeploymentEntity entity = deployment(deploymentId, applicationId, UUID.randomUUID());
        entity.setReplicas(2);
        entity.setTriggerType(TriggerType.ROLLBACK);
        entity.setStatus(DeploymentStatus.PENDING);
        entity.setImageRepository("ghcr.io/acme/demo");
        entity.setImageTag("v1");
        when(deployments.findById(deploymentId)).thenReturn(Optional.of(entity));
        when(applicationClient.get(eq("/internal/applications/" + applicationId), eq(RemoteApplication.class)))
                .thenReturn(application(applicationId));
        when(applicationClient.get(eq("/internal/applications/" + applicationId + "/env"), eq(RemoteEnvVar[].class)))
                .thenReturn(new RemoteEnvVar[0]);
        when(kubernetesClient.post(eq("/internal/kubernetes/deployments"), any(), eq(K8sDeploymentView.class)))
                .thenReturn(new K8sDeploymentView("dcp-demo", "dcp", "ghcr.io/acme/demo:v1", 2, 2, 9L, "RUNNING"));

        service.runWorkflow(deploymentId, true);

        assertEquals(DeploymentStatus.SUCCEEDED, entity.getStatus());
        assertEquals("ghcr.io/acme/demo", entity.getImageRepository());
        assertEquals("v1", entity.getImageTag());
        verifyNoInteractions(dockerClient);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(kubernetesClient).post(eq("/internal/kubernetes/deployments"), body.capture(), eq(K8sDeploymentView.class));
        assertEquals("ghcr.io/acme/demo:v1", ((K8sApplyRequest) body.getValue()).image());
    }

    private static DeploymentEntity deployment(UUID id, UUID applicationId, UUID ownerId) {
        DeploymentEntity entity = new DeploymentEntity();
        entity.setId(id);
        entity.setApplicationId(applicationId);
        entity.setOwnerId(ownerId);
        entity.setRequestedBy(ownerId);
        return entity;
    }

    private static RemoteApplication application(UUID applicationId) {
        return new RemoteApplication(
                applicationId,
                UUID.randomUUID(),
                "demo",
                null,
                "https://github.com/acme/demo",
                "main",
                "DEV",
                "Dockerfile",
                ".",
                8080,
                "CREATED",
                null,
                null);
    }

    private static void authenticate(UUID userId, String... roles) {
        JwtPrincipal principal = new JwtPrincipal(userId, userId + "@example.com", Set.of(roles));
        var authorities = java.util.Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
