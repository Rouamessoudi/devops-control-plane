package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerQueryServiceTest {

    @Mock
    private DockerBuildRepository builds;

    @Mock
    private DockerBuildStepRepository steps;

    @Mock
    private ManagedImageRepository images;

    @Mock
    private DockerGateway docker;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aMissingBuildAndAnotherUsersBuildAreNotFound() {
        UUID id = UUID.randomUUID();
        authenticate(UUID.randomUUID(), "DEVELOPER");
        when(builds.findById(id)).thenReturn(Optional.empty());
        DockerQueryService service = service();

        ApiException missing = assertThrows(ApiException.class, () -> service.build(id, false));
        assertEquals(404, missing.status());

        UUID owner = UUID.randomUUID();
        when(builds.findById(id)).thenReturn(Optional.of(build(id, owner)));
        ApiException hidden = assertThrows(ApiException.class, () -> service.build(id, false));
        assertEquals(404, hidden.status());
    }

    @Test
    void theOwnerSeesTheBuildStepsAndAnAdminSeesEveryBuild() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        DockerBuild row = build(id, owner);
        DockerBuildStep step = new DockerBuildStep();
        step.setName("image");
        step.setStatus("FAILED");
        step.setLogText("dockerfile missing");
        when(builds.findById(id)).thenReturn(Optional.of(row));
        when(steps.findByBuildIdOrderByStepOrderAsc(id)).thenReturn(List.of(step));
        authenticate(owner, "DEVELOPER");

        DockerApi.BuildView view = service().build(id, false);

        assertEquals("FAILED", view.steps().get(0).status());
        authenticate(UUID.randomUUID(), "ADMIN");
        when(builds.findAllByOrderByStartedAtDesc()).thenReturn(List.of(row));
        when(steps.findByBuildIdOrderByStepOrderAsc(id)).thenReturn(List.of());
        assertEquals(1, service().builds(null).size());
        assertEquals(1, service().audit().size());
    }

    @Test
    void containersAndLogsRequireAnAuthenticatedUser() {
        assertThrows(ApiException.class, () -> service().containers());
        authenticate(UUID.randomUUID(), "DEVELOPER");
        Container container = org.mockito.Mockito.mock(Container.class);
        when(container.getId()).thenReturn("abc");
        when(container.getImage()).thenReturn("localhost:15000/dcp/hello-dcp:1");
        when(container.getState()).thenReturn("running");
        when(container.getStatus()).thenReturn("Up");
        when(container.getNames()).thenReturn(new String[]{"/hello-dcp"});
        when(container.getCreated()).thenReturn(1_700_000_000L);
        when(docker.listManagedContainers()).thenReturn(List.of(container));
        when(docker.containerLogs("abc")).thenReturn("ready");

        List<DockerApi.ContainerView> containers = service().containers();
        assertEquals("abc", containers.get(0).id());
        assertEquals("/hello-dcp", containers.get(0).names().get(0));
        assertEquals("ready", service().logs("abc"));
    }

    @Test
    void aStoppedDaemonKeepsTheDatabaseImagesAndExplainsTheAbsence() {
        authenticate(UUID.randomUUID(), "ADMIN");
        ManagedImage image = new ManagedImage();
        image.setId(UUID.randomUUID());
        image.setBuildId(UUID.randomUUID());
        image.setRepository("localhost:15000/dcp/hello-dcp");
        image.setTag("1");
        image.setDockerImageId("sha256:abc");
        image.setSizeBytes(10L);
        image.setCreatedAt(Instant.parse("2026-10-11T20:00:00Z"));
        when(images.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(image));
        when(docker.listManagedImages()).thenThrow(ApiException.unavailable("Docker daemon is down"));

        DockerApi.ImageListing listing = service().images();

        assertEquals(1, listing.images().size());
        assertTrue(listing.warning().contains("Docker daemon is down"));
    }

    private DockerQueryService service() {
        return new DockerQueryService(builds, steps, images, docker);
    }

    private static DockerBuild build(UUID id, UUID owner) {
        DockerBuild row = new DockerBuild();
        row.setId(id);
        row.setApplicationId(UUID.randomUUID());
        row.setRequestedBy(owner);
        row.setRepositoryUrl("https://github.com/octocat/Hello-World");
        row.setBranch("master");
        row.setImageName("localhost:15000/dcp/hello-dcp");
        row.setImageTag("1");
        row.setFullImage("localhost:15000/dcp/hello-dcp:1");
        row.setStatus("FAILED");
        row.setLogText("failed");
        row.setErrorMessage("dockerfile missing");
        row.setStartedAt(Instant.parse("2026-10-11T20:00:00Z"));
        return row;
    }

    private static void authenticate(UUID userId, String role) {
        JwtPrincipal principal = new JwtPrincipal(userId, userId + "@dcp.local", Set.of(role));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }
}
