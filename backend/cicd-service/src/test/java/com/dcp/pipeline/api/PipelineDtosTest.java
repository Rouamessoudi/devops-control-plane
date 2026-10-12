package com.dcp.pipeline.api;

import com.dcp.pipeline.api.PipelineDtos.DeploymentView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PipelineDtosTest {

    private static final String PAYLOAD = """
            {
              "id": "47fd922d-893d-454f-952d-79b70d375e6b",
              "applicationId": "7b5208bb-8670-414a-abfb-b38f2f824c70",
              "requestedBy": "2b549cac-23db-4e9b-bf04-09c3942f1247",
              "imageRepository": "registry:5000/dcp/hello-dcp",
              "imageTag": "1",
              "fullImage": "registry:5000/dcp/hello-dcp:1",
              "replicas": 1,
              "environment": "dev",
              "trigger": "PIPELINE",
              "status": "SUCCEEDED",
              "k8sDeploymentName": "dcp-hello-dcp",
              "k8sRevision": 2,
              "errorMessage": null,
              "createdAt": "2026-10-11T20:00:00Z",
              "startedAt": "2026-10-11T20:00:00Z",
              "finishedAt": "2026-10-11T20:01:00Z",
              "ownerOnly": true
            }
            """;

    @Test
    void aDeploymentPayloadKeepsEveryFieldThePipelineCanRead() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DeploymentView view = mapper.readValue(PAYLOAD, DeploymentView.class);

        assertEquals(UUID.fromString("47fd922d-893d-454f-952d-79b70d375e6b"), view.id());
        assertEquals(UUID.fromString("7b5208bb-8670-414a-abfb-b38f2f824c70"), view.applicationId());
        assertEquals(UUID.fromString("2b549cac-23db-4e9b-bf04-09c3942f1247"), view.requestedBy());
        assertEquals("registry:5000/dcp/hello-dcp", view.imageRepository());
        assertEquals("1", view.imageTag());
        assertEquals("registry:5000/dcp/hello-dcp:1", view.fullImage());
        assertEquals(1, view.replicas());
        assertEquals("dev", view.environment());
        assertEquals("PIPELINE", view.trigger());
        assertEquals("SUCCEEDED", view.status());
        assertEquals("dcp-hello-dcp", view.k8sDeploymentName());
        assertEquals(2, view.k8sRevision());
        assertNull(view.errorMessage());
        assertEquals(Instant.parse("2026-10-11T20:00:00Z"), view.createdAt());
        assertEquals(Instant.parse("2026-10-11T20:00:00Z"), view.startedAt());
        assertEquals(Instant.parse("2026-10-11T20:01:00Z"), view.finishedAt());
    }
}
