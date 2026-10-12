package com.dcp.pipeline.service;

import com.dcp.common.web.InternalRestClient;
import com.dcp.pipeline.api.PipelineDtos.DeploymentView;
import com.dcp.pipeline.sonar.SonarPolicy;
import com.dcp.pipeline.sonar.SonarPolicy.SonarDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineRunnerTest {

    @Mock
    private PipelineStore store;

    @Mock
    private InternalRestClient deployment;

    @Mock
    private InternalRestClient notifications;

    @Mock
    private SonarPolicy sonar;

    @Test
    void aFailedQualityGateDoesNotDeploy() {
        when(sonar.evaluate()).thenReturn(SonarDecision.block("Quality gate ERROR. Deployment is blocked."));
        UUID pipelineId = UUID.randomUUID();

        runner().runLocal(pipelineId, UUID.randomUUID(), UUID.randomUUID());

        verify(store).recordQuality(pipelineId, "FAILED", "Quality gate ERROR. Deployment is blocked.");
        verify(deployment, never()).post(anyString(), any(), eq(DeploymentView.class));
    }

    @Test
    void aPassedQualityGateContinuesTheDeployment() {
        when(sonar.evaluate()).thenReturn(SonarDecision.pass("Quality gate read from SonarQube: OK"));
        DeploymentView created = new DeploymentView(
                UUID.randomUUID(), null, null, null, null, null, null, null, null,
                "SUCCEEDED", null, null, null, null, null, null);
        when(deployment.post(eq("/internal/deployments"), any(), eq(DeploymentView.class))).thenReturn(created);
        UUID pipelineId = UUID.randomUUID();

        runner().runLocal(pipelineId, UUID.randomUUID(), UUID.randomUUID());

        verify(store).recordQuality(pipelineId, "SUCCEEDED", "Quality gate read from SonarQube: OK");
        verify(deployment).post(eq("/internal/deployments"), any(), eq(DeploymentView.class));
        verify(store).finishFromDeployment(pipelineId, created);
    }

    @Test
    void aMissingDeploymentIdFailsTheOpenSteps() {
        when(sonar.evaluate()).thenReturn(SonarDecision.pass("Quality gate read from SonarQube: OK"));
        when(deployment.post(eq("/internal/deployments"), any(), eq(DeploymentView.class))).thenReturn(null);
        UUID pipelineId = UUID.randomUUID();

        runner().runLocal(pipelineId, UUID.randomUUID(), UUID.randomUUID());

        verify(store).failOpenSteps(pipelineId, "Deployment service did not return a deployment id");
    }

    @Test
    void aDeploymentWithoutAnIdFailsTheOpenSteps() {
        when(sonar.evaluate()).thenReturn(SonarDecision.pass("Quality gate read from SonarQube: OK"));
        DeploymentView created = new DeploymentView(
                null, null, null, null, null, null, null, null, null,
                "RUNNING", null, null, null, null, null, null);
        when(deployment.post(eq("/internal/deployments"), any(), eq(DeploymentView.class))).thenReturn(created);
        UUID pipelineId = UUID.randomUUID();

        runner().runLocal(pipelineId, UUID.randomUUID(), UUID.randomUUID());

        verify(store).failOpenSteps(pipelineId, "Deployment service did not return a deployment id");
    }

    @Test
    void aRunningDeploymentIsPolledUntilItSucceeds() {
        when(sonar.evaluate()).thenReturn(SonarDecision.pass("Quality gate read from SonarQube: OK"));
        UUID deploymentId = UUID.randomUUID();
        DeploymentView created = new DeploymentView(
                deploymentId, null, null, null, null, null, null, null, null,
                "RUNNING", null, null, null, null, null, null);
        DeploymentView finished = new DeploymentView(
                deploymentId, null, null, null, null, "registry:5000/dcp/hello-dcp:1", null, null, null,
                "SUCCEEDED", "dcp-hello-dcp", 1, null, null, null, null);
        when(deployment.post(eq("/internal/deployments"), any(), eq(DeploymentView.class))).thenReturn(created);
        when(deployment.get(eq("/internal/deployments/" + deploymentId), eq(DeploymentView.class))).thenReturn(finished);
        UUID pipelineId = UUID.randomUUID();

        runner().runLocal(pipelineId, UUID.randomUUID(), UUID.randomUUID());

        verify(store).markBuildRunning(pipelineId);
        verify(store).finishFromDeployment(pipelineId, finished);
    }

    private PipelineRunner runner() {
        return new PipelineRunner(store, deployment, notifications, sonar);
    }
}
