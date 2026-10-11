package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DockerfilePathTest {

    @Test
    void dockerfilePathContainingDotDotIsRejectedBeforeAnyDockerCall() {
        DockerClient client = mock(DockerClient.class);
        DockerGateway gateway = new DockerGateway(client);
        ApiException ex = assertThrows(ApiException.class, () -> gateway.buildImage(
                Path.of("workspace"),
                "../Dockerfile",
                ".",
                "dcp/demo:1",
                UUID.randomUUID()));
        assertEquals(400, ex.status());
        verifyNoInteractions(client);
    }
}
