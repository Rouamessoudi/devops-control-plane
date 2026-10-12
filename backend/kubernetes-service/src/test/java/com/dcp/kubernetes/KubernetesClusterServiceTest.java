package com.dcp.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KubernetesClusterServiceTest {

    @Mock
    private KubernetesClient client;

    @Mock
    private OperationLog operations;

    @Test
    void statusIsReachableWhenTheApiAnswers() {
        when(client.getKubernetesVersion()).thenReturn(null);

        KubernetesApi.ClusterStatus status = service().status();

        assertTrue(status.reachable());
        assertTrue(status.message().contains("reachable"));
    }

    @Test
    void statusStaysUnreachableWhenTheApiThrows() {
        when(client.getKubernetesVersion()).thenThrow(new IllegalStateException("connection refused"));

        KubernetesApi.ClusterStatus status = service().status();

        assertFalse(status.reachable());
        assertTrue(status.message().contains("connection refused"));
    }

    @Test
    void statusTimesOutWhenTheApiDoesNotAnswer() {
        when(client.getKubernetesVersion()).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return null;
        });

        KubernetesApi.ClusterStatus status = service().status();

        assertFalse(status.reachable());
        assertTrue(status.message().contains("timed out"));
    }

    private KubernetesClusterService service() {
        return new KubernetesClusterService(client, new KubernetesProperties(), operations);
    }
}
