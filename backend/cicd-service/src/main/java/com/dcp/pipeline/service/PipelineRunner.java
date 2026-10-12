package com.dcp.pipeline.service;

import com.dcp.common.web.InternalRestClient;
import com.dcp.pipeline.api.PipelineDtos.DeploymentCommand;
import com.dcp.pipeline.api.PipelineDtos.DeploymentView;
import com.dcp.pipeline.api.PipelineDtos.NotificationCommand;
import com.dcp.pipeline.api.PipelineDtos.PipelineView;
import com.dcp.pipeline.sonar.SonarPolicy;
import com.dcp.pipeline.sonar.SonarPolicy.SonarDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(10);

    private final PipelineStore store;
    private final InternalRestClient deploymentClient;
    private final InternalRestClient notificationClient;
    private final SonarPolicy sonar;

    public PipelineRunner(PipelineStore store,
                          @Qualifier("deploymentClient") InternalRestClient deploymentClient,
                          @Qualifier("notificationClient") InternalRestClient notificationClient,
                          SonarPolicy sonar) {
        this.store = store;
        this.deploymentClient = deploymentClient;
        this.notificationClient = notificationClient;
        this.sonar = sonar;
    }

    public void runLocal(UUID pipelineId, UUID applicationId, UUID requestedBy) {
        try {
            store.markPrepared(pipelineId);
            SonarDecision decision = sonar.evaluate();
            if (decision.skip()) {
                store.recordQuality(pipelineId, PipelineStore.SKIPPED, decision.log());
            } else if (!decision.pass()) {
                store.recordQuality(pipelineId, PipelineStore.FAILED, decision.log());
                notify(pipelineId);
                return;
            } else {
                store.recordQuality(pipelineId, PipelineStore.SUCCEEDED, decision.log());
            }
            DeploymentView created = deploymentClient.post(
                    "/internal/deployments",
                    new DeploymentCommand(applicationId, requestedBy, 1, "PIPELINE"),
                    DeploymentView.class);
            if (created == null || created.id() == null) {
                store.failOpenSteps(pipelineId, "Deployment service did not return a deployment id");
            } else if (terminal(created.status())) {
                store.finishFromDeployment(pipelineId, created);
            } else {
                store.markBuildRunning(pipelineId);
                DeploymentView latest = poll(created);
                store.finishFromDeployment(pipelineId, latest);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Local pipeline {} was interrupted", pipelineId);
            store.failOpenSteps(pipelineId, "Pipeline execution was interrupted");
        } catch (Exception ex) {
            log.warn("Local pipeline {} failed: {}", pipelineId, ex.getMessage());
            store.failOpenSteps(pipelineId, message(ex));
        }
        notify(pipelineId);
    }

    private DeploymentView poll(DeploymentView created) throws InterruptedException {
        DeploymentView latest = created;
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (!terminal(latest.status())) {
            if (!Instant.now().isBefore(deadline)) {
                return latest;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
            latest = deploymentClient.get("/internal/deployments/" + created.id(), DeploymentView.class);
            if (latest == null) {
                throw new IllegalStateException("Deployment service returned an empty deployment");
            }
        }
        return latest;
    }

    private void notify(UUID pipelineId) {
        try {
            PipelineView pipeline = store.read(pipelineId);
            notificationClient.post("/internal/notifications", new NotificationCommand(
                    pipeline.requestedBy(),
                    "Pipeline " + pipeline.status(),
                    "Pipeline " + pipeline.id() + " finished with " + pipeline.conclusion()));
        } catch (Exception ex) {
            log.warn("Notification was not delivered for pipeline {}: {}", pipelineId, ex.getMessage());
        }
    }

    private static boolean terminal(String status) {
        return "SUCCEEDED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
    }

    private static String message(Exception ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) {
            return "Pipeline failed";
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
