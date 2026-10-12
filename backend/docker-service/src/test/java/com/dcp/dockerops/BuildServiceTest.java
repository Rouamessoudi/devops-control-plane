package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.InternalRestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildServiceTest {

    @Mock
    private BuildStore store;

    @Mock
    private BuildJob job;

    @Mock
    private InternalRestClient applications;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anotherUserCannotBuildTheApplication() {
        UUID owner = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(caller);
        when(applications.get("/internal/applications/" + applicationId, ApplicationView.class))
                .thenReturn(application(applicationId, owner));

        ApiException exception = assertThrows(ApiException.class,
                () -> service(Runnable::run).acceptFromUser(new DockerApi.UserBuildRequest(applicationId, "1")));

        assertEquals(403, exception.status());
        verify(store, never()).insert(any());
    }

    @Test
    void anEmptyOrganizationAllowlistBlocksTheBuildWhenItIsEnforced() {
        UUID owner = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(owner);
        when(applications.get("/internal/applications/" + applicationId, ApplicationView.class))
                .thenReturn(application(applicationId, owner));
        GithubProperties github = new GithubProperties();
        github.setEnforceAllowlist(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service(Runnable::run, github).acceptFromUser(new DockerApi.UserBuildRequest(applicationId, "1")));

        assertEquals(403, exception.status());
        verify(store, never()).insert(any());
    }

    @Test
    void aSaturatedExecutorFailsTheBuild() {
        UUID owner = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(owner);
        when(applications.get("/internal/applications/" + applicationId, ApplicationView.class))
                .thenReturn(application(applicationId, owner));
        when(store.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Executor saturated = command -> {
            throw new RejectedExecutionException("full");
        };

        ApiException exception = assertThrows(ApiException.class,
                () -> service(saturated).acceptFromUser(new DockerApi.UserBuildRequest(applicationId, "1")));

        assertEquals(503, exception.status());
        verify(store).fail(any(), eq("Build executor is saturated"));
    }

    @Test
    void theOwnerBuildIsAcceptedAndQueued() {
        UUID owner = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        authenticate(owner);
        when(applications.get("/internal/applications/" + applicationId, ApplicationView.class))
                .thenReturn(application(applicationId, owner));
        when(store.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DockerApi.BuildAccepted accepted = service(Runnable::run)
                .acceptFromUser(new DockerApi.UserBuildRequest(applicationId, "1"));

        assertEquals("PENDING", accepted.status());
        assertEquals("1", accepted.imageTag());
        assertEquals("localhost:15000/dcp/hello-dcp:1", accepted.fullImage());
        verify(job).run(any(), eq("Dockerfile"), eq("."), eq("localhost:15000/dcp/hello-dcp:1"));
    }

    @Test
    void anInternalBuildUsesTheApplicationDefaultsWhenFieldsAreBlank() {
        UUID owner = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applications.get("/internal/applications/" + applicationId, ApplicationView.class))
                .thenReturn(application(applicationId, owner));
        when(store.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DockerApi.BuildAccepted accepted = service(command -> { })
                .acceptInternal(new DockerApi.BuildRequest(applicationId, owner, " ", " ", " ", " ", "2"));

        assertEquals("localhost:15000/dcp/hello-dcp:2", accepted.fullImage());
        verify(job, never()).run(any(), any(), any(), any());
    }

    private BuildService service(Executor executor) {
        GithubProperties github = new GithubProperties();
        return service(executor, github);
    }

    private BuildService service(Executor executor, GithubProperties github) {
        DockerProperties docker = new DockerProperties();
        docker.setRegistry("registry:5000");
        docker.setPublicRegistry("localhost:15000");
        return new BuildService(store, job, applications, docker, github, executor);
    }

    private static ApplicationView application(UUID id, UUID owner) {
        return new ApplicationView(id, owner, "hello-dcp", "demo", "https://github.com/octocat/Hello-World",
                "master", "DEV", "Dockerfile", ".", 80, "READY", Instant.now(), Instant.now());
    }

    private static void authenticate(UUID userId) {
        JwtPrincipal principal = new JwtPrincipal(userId, userId + "@dcp.local", Set.of("DEVELOPER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }
}
