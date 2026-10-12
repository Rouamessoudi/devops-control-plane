package com.dcp.dockerops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildJobTest {

    @Mock
    private BuildStore store;

    @Mock
    private DockerGateway docker;

    @Test
    void workspaceIsCreatedOutsideThePublicTempDirectory() throws Exception {
        Path workspace = BuildJob.privateWorkspace();
        try {
            Path root = Path.of(System.getProperty("user.home"), ".dcp", "builds");
            assertTrue(workspace.startsWith(root));
            assertFalse(workspace.startsWith(Path.of(System.getProperty("java.io.tmpdir"))));
            assertTrue(Files.isDirectory(workspace));
        } finally {
            Files.deleteIfExists(workspace);
        }
    }

    @Test
    void aFailureMessageDoesNotKeepTheGithubToken() {
        String secret = "ghp-test-token";
        GithubProperties github = new GithubProperties();
        github.setToken(secret);
        BuildJob job = new BuildJob(store, docker, github, new DockerProperties());
        UUID id = UUID.randomUUID();
        when(store.require(id)).thenThrow(new IllegalStateException("clone failed " + secret));

        job.run(id, "Dockerfile", ".", "localhost:15000/dcp/demo:1");

        verify(store).fail(eq(id), argThat(message -> message.contains("***") && !message.contains(secret)));
        verify(docker, never()).buildImage(any(), any(), any(), any(), any());
    }
}
