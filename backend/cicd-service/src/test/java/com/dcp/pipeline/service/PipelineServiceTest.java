package com.dcp.pipeline.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.InternalRestClient;
import com.dcp.pipeline.api.PipelineDtos.PipelineView;
import com.dcp.pipeline.api.PipelineDtos.RemoteApplication;
import com.dcp.pipeline.config.GithubProperties;
import com.dcp.pipeline.config.PipelineProperties;
import com.dcp.pipeline.github.GithubWorkflowClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    @Mock
    private PipelineStore store;

    @Mock
    private PipelineRunner runner;

    @Mock
    private InternalRestClient applications;

    @Mock
    private GithubWorkflowClient github;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anotherUserCannotStartThePipeline() {
        UUID owner = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(caller);
        when(applications.get("/internal/applications/" + applicationId, RemoteApplication.class))
                .thenReturn(application(applicationId, owner));

        ApiException exception = assertThrows(ApiException.class, () -> service("local", "token").start(applicationId));

        assertEquals(403, exception.status());
        verify(store, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void githubModeWithoutATokenFailsClosed() {
        UUID owner = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        authenticate(owner);
        when(applications.get("/internal/applications/" + applicationId, RemoteApplication.class))
                .thenReturn(application(applicationId, owner));
        PipelineView created = view(pipelineId, applicationId, owner, "RUNNING");
        when(store.create(eq(owner), eq(applicationId), eq("https://github.com/acme/demo"), eq("main"), any()))
                .thenReturn(created);
        when(store.failGithub(pipelineId, "GitHub token is not configured"))
                .thenReturn(view(pipelineId, applicationId, owner, "FAILED"));

        PipelineView result = service("github", "  ").start(applicationId);

        assertEquals("FAILED", result.status());
        verify(github, never()).dispatch(anyString(), anyString(), anyString(), anyString(), any());
    }

    private PipelineService service(String mode, String token) {
        PipelineProperties pipelineProperties = new PipelineProperties();
        pipelineProperties.setMode(mode);
        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setToken(token);
        return new PipelineService(store, runner, applications, github, pipelineProperties, githubProperties, task -> {
            throw new AssertionError("local runner must not start");
        });
    }

    private static RemoteApplication application(UUID id, UUID owner) {
        RemoteApplication application = new RemoteApplication();
        application.setId(id);
        application.setOwnerId(owner);
        application.setGitRepositoryUrl("https://github.com/acme/demo");
        application.setBranch("main");
        return application;
    }

    private static PipelineView view(UUID id, UUID applicationId, UUID owner, String status) {
        return new PipelineView(id, applicationId, owner, "https://github.com/acme/demo", "main", status, "MANUAL", null,
                Instant.parse("2026-10-11T00:00:00Z"), null, List.of());
    }

    private static void authenticate(UUID userId) {
        JwtPrincipal principal = new JwtPrincipal(userId, userId + "@example.com", Set.of("DEVELOPER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
