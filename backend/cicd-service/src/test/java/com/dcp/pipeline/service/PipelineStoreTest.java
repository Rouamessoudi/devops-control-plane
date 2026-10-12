package com.dcp.pipeline.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.pipeline.api.PipelineDtos.DeploymentView;
import com.dcp.pipeline.api.PipelineDtos.PipelineView;
import com.dcp.pipeline.domain.PipelineEntity;
import com.dcp.pipeline.domain.PipelineRepository;
import com.dcp.pipeline.domain.PipelineStepEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineStoreTest {

    @Mock
    private PipelineRepository pipelines;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aFailedQualityGateClosesThePipelineBeforeDeployment() {
        PipelineStore store = store();
        PipelineView created = store.create(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("prepare", "sonar-analysis", "quality-gate", "build-and-test", "deploy", "verify"));
        store.markPrepared(created.id());

        store.recordQuality(created.id(), "FAILED", "Quality gate ERROR. Deployment is blocked.");

        PipelineView view = store.read(created.id());
        assertEquals("FAILED", view.status());
        assertEquals("failure", view.conclusion());
        assertEquals("FAILED", step(view, "sonar-analysis").status());
        assertEquals("FAILED", step(view, "quality-gate").status());
        assertEquals("Not started because the quality gate blocked the deployment", step(view, "deploy").log());
    }

    @Test
    void aSuccessfulDeploymentRecordsTheImageAndClosesThePipeline() {
        PipelineStore store = store();
        PipelineView created = store.create(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("prepare", "build-and-test", "deploy", "verify"));
        DeploymentView deployment = new DeploymentView(
                null, null, null, null, null,
                "registry:5000/dcp/hello-dcp:1",
                null, null, null,
                "SUCCEEDED",
                "dcp-hello-dcp",
                2,
                null, null,
                Instant.parse("2026-10-11T20:00:00Z"),
                Instant.parse("2026-10-11T20:01:00Z"));

        store.finishFromDeployment(created.id(), deployment);

        PipelineView view = store.read(created.id());
        assertEquals("SUCCEEDED", view.status());
        assertTrue(step(view, "build-and-test").log().contains("registry:5000/dcp/hello-dcp:1"));
        assertTrue(step(view, "deploy").log().contains("dcp-hello-dcp"));
    }

    @Test
    void aSuccessfulDeploymentWithoutAnImageStillClosesThePipeline() {
        PipelineStore store = store();
        PipelineView created = store.create(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("build-and-test", "deploy", "verify"));
        DeploymentView deployment = new DeploymentView(
                null, null, null, null, null, null, null, null, null,
                "SUCCEEDED", null, null, null, null,
                Instant.parse("2026-10-11T20:00:00Z"),
                Instant.parse("2026-10-11T20:01:00Z"));

        store.finishFromDeployment(created.id(), deployment);

        PipelineView view = store.read(created.id());
        assertEquals("SUCCEEDED", view.status());
        assertEquals("Deployment succeeded", step(view, "build-and-test").log());
        assertTrue(step(view, "deploy").log().contains("k8sDeploymentName= revision="));
    }

    @Test
    void aFailedDeploymentKeepsTheErrorMessageAndTheStartTime() {
        PipelineStore store = store();
        PipelineView created = store.create(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("build-and-test", "deploy", "verify"));
        DeploymentView deployment = new DeploymentView(
                null, null, null, null, null, null, null, null, null,
                "FAILED", null, null, "image pull failed", null,
                Instant.parse("2026-10-11T20:00:00Z"), null);

        store.finishFromDeployment(created.id(), deployment);

        PipelineView view = store.read(created.id());
        assertEquals("FAILED", view.status());
        assertEquals("image pull failed", step(view, "build-and-test").log());
        assertEquals(Instant.parse("2026-10-11T20:00:00Z"), step(view, "build-and-test").startedAt());
    }

    @Test
    void aMissingDeploymentDoesNotReadANullStatus() {
        PipelineStore store = store();
        PipelineView created = store.create(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("build-and-test", "deploy", "verify"));

        store.finishFromDeployment(created.id(), null);

        PipelineView view = store.read(created.id());
        assertEquals("FAILED", view.status());
        assertEquals("Deployment did not reach a terminal state within 10 minutes", step(view, "build-and-test").log());
    }

    @Test
    void anUnknownPipelineIsNotFoundAndAWebhookWithoutItDoesNothing() {
        PipelineStore store = store();

        ApiException missing = assertThrows(ApiException.class, () -> store.read(UUID.randomUUID()));
        assertEquals(404, missing.status());
        assertNull(store.applyWebhook(UUID.randomUUID(), "delivery", "workflow_job", "success"));
    }

    @Test
    void aWebhookSuccessAndFailureCloseTheAwaitingStep() {
        PipelineStore store = store();
        PipelineView created = store.create(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("dispatch", "await-webhook"));
        store.markDispatched(created.id(), "deploy.yml", "master");

        PipelineView success = store.applyWebhook(created.id(), "abc", "workflow_job", "success");
        assertEquals("SUCCEEDED", success.status());
        assertTrue(step(success, "await-webhook").log().contains("delivery=abc"));

        PipelineView second = store.create(UUID.randomUUID(), UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("dispatch", "await-webhook"));
        PipelineView failure = store.applyWebhook(second.id(), "def", "", "failure");
        assertEquals("FAILED", failure.status());
        assertNull(store.applyWebhook(second.id(), "ghi", "push", "ignored"));
    }

    @Test
    void anotherUserCannotReadThePipelineLogs() {
        UUID owner = UUID.randomUUID();
        PipelineStore store = store();
        PipelineView created = store.create(owner, UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("prepare"));
        authenticate(UUID.randomUUID(), "DEVELOPER");

        ApiException exception = assertThrows(ApiException.class, () -> store.logs(created.id()));

        assertEquals(403, exception.status());
    }

    @Test
    void aDeveloperListsOnlyTheirOwnPipelines() {
        UUID owner = UUID.randomUUID();
        PipelineStore store = store();
        PipelineView created = store.create(owner, UUID.randomUUID(), "https://github.com/octocat/Hello-World", "master",
                List.of("prepare"));
        PipelineEntity entity = pipelines.findWithStepsById(created.id()).orElseThrow();
        when(pipelines.findByRequestedBy(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        authenticate(owner, "DEVELOPER");

        assertEquals(1, store.list(null, 0, 20).total());
        assertTrue(store.logs(created.id()).contains("prepare"));
    }

    private PipelineStore store() {
        AtomicReference<PipelineEntity> current = new AtomicReference<>();
        when(pipelines.save(any())).thenAnswer(invocation -> {
            PipelineEntity entity = invocation.getArgument(0);
            current.set(entity);
            return entity;
        });
        when(pipelines.findWithStepsById(any())).thenAnswer(invocation -> {
            PipelineEntity entity = current.get();
            if (entity != null && entity.getId().equals(invocation.getArgument(0))) {
                return Optional.of(entity);
            }
            return Optional.empty();
        });
        return new PipelineStore(pipelines);
    }

    private static com.dcp.pipeline.api.PipelineDtos.StepView step(PipelineView view, String name) {
        return view.steps().stream().filter(item -> name.equals(item.name())).findFirst().orElseThrow();
    }

    private static void authenticate(UUID userId, String role) {
        JwtPrincipal principal = new JwtPrincipal(userId, userId + "@dcp.local", Set.of(role));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }
}
