package com.dcp.pipeline.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.dcp.common.web.PageResponse;
import com.dcp.pipeline.api.PipelineDtos.DeploymentView;
import com.dcp.pipeline.api.PipelineDtos.PipelineAuditEvent;
import com.dcp.pipeline.api.PipelineDtos.PipelineView;
import com.dcp.pipeline.api.PipelineDtos.StepView;
import com.dcp.pipeline.domain.PipelineEntity;
import com.dcp.pipeline.domain.PipelineRepository;
import com.dcp.pipeline.domain.PipelineStepEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PipelineStore {

    static final String RUNNING = "RUNNING";
    static final String SUCCEEDED = "SUCCEEDED";
    static final String FAILED = "FAILED";
    static final String PENDING = "PENDING";
    static final String MANUAL = "MANUAL";
    static final String SUCCESS = "success";
    static final String FAILURE = "failure";

    static final String PREPARE = "prepare";
    static final String BUILD = "build-and-test";
    static final String DEPLOY = "deploy";
    static final String VERIFY = "verify";
    static final String DISPATCH = "dispatch";
    static final String AWAIT = "await-webhook";

    private final PipelineRepository pipelines;

    public PipelineStore(PipelineRepository pipelines) {
        this.pipelines = pipelines;
    }

    @Transactional
    public PipelineView create(UUID requestedBy, UUID applicationId, String repositoryUrl, String branch, List<String> stepNames) {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(UUID.randomUUID());
        pipeline.setApplicationId(applicationId);
        pipeline.setRequestedBy(requestedBy);
        pipeline.setRepositoryUrl(repositoryUrl);
        pipeline.setBranch(branch);
        pipeline.setStatus(RUNNING);
        pipeline.setTriggerType(MANUAL);
        pipeline.setStartedAt(Instant.now());
        int order = 1;
        for (String name : stepNames) {
            PipelineStepEntity step = new PipelineStepEntity();
            step.setId(UUID.randomUUID());
            step.setPipeline(pipeline);
            step.setStepOrder(order++);
            step.setName(name);
            step.setStatus(PENDING);
            step.setLogText("");
            pipeline.getSteps().add(step);
        }
        return view(pipelines.save(pipeline));
    }

    @Transactional
    public void markPrepared(UUID id) {
        PipelineEntity pipeline = load(id);
        PipelineStepEntity step = requireStep(pipeline, PREPARE);
        Instant now = Instant.now();
        step.setStatus(SUCCEEDED);
        step.setStartedAt(now);
        step.setFinishedAt(now);
        step.setLogText("repository=" + pipeline.getRepositoryUrl() + "\nbranch=" + pipeline.getBranch());
    }

    @Transactional
    public void markBuildRunning(UUID id) {
        PipelineEntity pipeline = load(id);
        PipelineStepEntity step = requireStep(pipeline, BUILD);
        step.setStatus(RUNNING);
        if (step.getStartedAt() == null) {
            step.setStartedAt(Instant.now());
        }
        step.setLogText("Waiting for the deployment to reach a terminal state");
    }

    @Transactional
    public void finishFromDeployment(UUID id, DeploymentView deployment) {
        PipelineEntity pipeline = load(id);
        String status = deployment == null ? null : deployment.getStatus();
        if ("SUCCEEDED".equalsIgnoreCase(status)) {
            String image = deployment.getFullImage() == null ? "" : deployment.getFullImage();
            String buildLog = image.isBlank() ? "Deployment succeeded" : "image=" + image;
            complete(requireStep(pipeline, BUILD), SUCCEEDED, buildLog, deployment.getStartedAt(), deployment.getFinishedAt());
            complete(requireStep(pipeline, DEPLOY), SUCCEEDED, kubernetesLog(deployment), deployment.getStartedAt(), deployment.getFinishedAt());
            complete(requireStep(pipeline, VERIFY), SUCCEEDED, kubernetesLog(deployment), Instant.now(), Instant.now());
            close(pipeline, SUCCEEDED, SUCCESS);
            return;
        }
        String error = failureMessage(deployment, status);
        complete(requireStep(pipeline, BUILD), FAILED, error, started(deployment), Instant.now());
        complete(requireStep(pipeline, DEPLOY), FAILED, error, started(deployment), Instant.now());
        complete(requireStep(pipeline, VERIFY), FAILED, error, Instant.now(), Instant.now());
        close(pipeline, FAILED, FAILURE);
    }

    @Transactional
    public void failOpenSteps(UUID id, String message) {
        PipelineEntity pipeline = load(id);
        if (FAILED.equals(pipeline.getStatus()) || SUCCEEDED.equals(pipeline.getStatus())) {
            return;
        }
        String text = message == null || message.isBlank() ? "Pipeline failed" : message;
        Instant now = Instant.now();
        for (PipelineStepEntity step : pipeline.getSteps()) {
            if (!SUCCEEDED.equals(step.getStatus())) {
                if (step.getStartedAt() == null) {
                    step.setStartedAt(now);
                }
                step.setStatus(FAILED);
                step.setFinishedAt(now);
                step.setLogText(append(step.getLogText(), text));
            }
        }
        close(pipeline, FAILED, FAILURE);
    }

    @Transactional
    public PipelineView failGithub(UUID id, String message) {
        PipelineEntity pipeline = load(id);
        Instant now = Instant.now();
        String text = message == null || message.isBlank() ? "GitHub pipeline failed" : message;
        for (PipelineStepEntity step : pipeline.getSteps()) {
            step.setStatus(FAILED);
            if (step.getStartedAt() == null) {
                step.setStartedAt(now);
            }
            step.setFinishedAt(now);
            step.setLogText(text);
        }
        close(pipeline, FAILED, FAILURE);
        return view(pipeline);
    }

    @Transactional
    public PipelineView markDispatched(UUID id, String workflow, String branch) {
        PipelineEntity pipeline = load(id);
        Instant now = Instant.now();
        PipelineStepEntity dispatch = requireStep(pipeline, DISPATCH);
        dispatch.setStatus(SUCCEEDED);
        dispatch.setStartedAt(now);
        dispatch.setFinishedAt(now);
        dispatch.setLogText("Dispatched workflow " + workflow + " on " + branch);
        PipelineStepEntity await = requireStep(pipeline, AWAIT);
        await.setStatus(RUNNING);
        await.setStartedAt(now);
        await.setLogText("Waiting for a signed GitHub webhook");
        pipeline.setStatus(RUNNING);
        pipeline.setConclusion(null);
        pipeline.setFinishedAt(null);
        return view(pipeline);
    }

    @Transactional
    public PipelineView applyWebhook(UUID pipelineId, String deliveryId, String eventType, String outcome) {
        PipelineEntity pipeline = pipelines.findWithStepsById(pipelineId).orElse(null);
        if (pipeline == null) {
            return null;
        }
        pipeline.setGithubDeliveryId(deliveryId);
        String line = (eventType == null || eventType.isBlank() ? "webhook" : eventType) + " delivery=" + deliveryId;
        PipelineStepEntity await = findStep(pipeline, AWAIT);
        if (await != null) {
            await.setLogText(append(await.getLogText(), line));
        } else if (!pipeline.getSteps().isEmpty()) {
            List<PipelineStepEntity> ordered = ordered(pipeline);
            PipelineStepEntity last = ordered.get(ordered.size() - 1);
            last.setLogText(append(last.getLogText(), line));
        }
        if (SUCCESS.equals(outcome)) {
            finishWebhook(pipeline, await, SUCCEEDED, SUCCESS);
            return view(pipeline);
        }
        if (FAILURE.equals(outcome)) {
            finishWebhook(pipeline, await, FAILED, FAILURE);
            return view(pipeline);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public PageResponse<PipelineView> list(UUID applicationId, int page, int size) {
        JwtPrincipal user = CurrentUser.require();
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<PipelineEntity> result;
        if (user.hasRole("ADMIN")) {
            result = applicationId == null
                    ? pipelines.findAll(request)
                    : pipelines.findByApplicationId(applicationId, request);
        } else if (applicationId == null) {
            result = pipelines.findByRequestedBy(user.userId(), request);
        } else {
            result = pipelines.findByApplicationIdAndRequestedBy(applicationId, user.userId(), request);
        }
        List<PipelineView> items = new ArrayList<>();
        for (PipelineEntity pipeline : result.getContent()) {
            items.add(view(pipeline));
        }
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PipelineView get(UUID id) {
        return view(visible(id));
    }

    @Transactional(readOnly = true)
    public PipelineView read(UUID id) {
        return view(load(id));
    }

    @Transactional(readOnly = true)
    public String logs(UUID id) {
        PipelineEntity pipeline = visible(id);
        StringBuilder builder = new StringBuilder();
        for (PipelineStepEntity step : ordered(pipeline)) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(step.getName()).append(' ').append(step.getStatus()).append('\n');
            builder.append(step.getLogText() == null ? "" : step.getLogText());
        }
        return builder.toString();
    }

    @Transactional(readOnly = true)
    public List<PipelineAuditEvent> audit() {
        JwtPrincipal user = CurrentUser.require();
        PageRequest request = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<PipelineEntity> result = user.hasRole("ADMIN")
                ? pipelines.findAll(request)
                : pipelines.findByRequestedBy(user.userId(), request);
        List<PipelineAuditEvent> events = new ArrayList<>();
        for (PipelineEntity pipeline : result.getContent()) {
            events.add(new PipelineAuditEvent(
                    pipeline.getId(),
                    "PIPELINE_" + pipeline.getStatus(),
                    pipeline.getApplicationId(),
                    pipeline.getRequestedBy(),
                    pipeline.getStatus(),
                    pipeline.getTriggerType(),
                    pipeline.getConclusion(),
                    pipeline.getStartedAt()));
        }
        return events;
    }

    private void finishWebhook(PipelineEntity pipeline, PipelineStepEntity await, String status, String conclusion) {
        Instant now = Instant.now();
        if (await != null) {
            await.setStatus(status);
            if (await.getStartedAt() == null) {
                await.setStartedAt(now);
            }
            await.setFinishedAt(now);
        }
        close(pipeline, status, conclusion);
    }

    private PipelineEntity visible(UUID id) {
        JwtPrincipal user = CurrentUser.require();
        PipelineEntity pipeline = load(id);
        if (!user.hasRole("ADMIN") && !user.userId().equals(pipeline.getRequestedBy())) {
            throw ApiException.forbidden("You cannot view this pipeline");
        }
        return pipeline;
    }

    private PipelineEntity load(UUID id) {
        return pipelines.findWithStepsById(id).orElseThrow(() -> ApiException.notFound("Pipeline not found"));
    }

    private static void close(PipelineEntity pipeline, String status, String conclusion) {
        pipeline.setStatus(status);
        pipeline.setConclusion(conclusion);
        pipeline.setFinishedAt(Instant.now());
    }

    private static void complete(PipelineStepEntity step, String status, String log, Instant started, Instant finished) {
        Instant now = Instant.now();
        step.setStatus(status);
        if (step.getStartedAt() == null) {
            step.setStartedAt(started == null ? now : started);
        }
        step.setFinishedAt(finished == null ? now : finished);
        step.setLogText(log == null ? "" : log);
    }

    private static String failureMessage(DeploymentView deployment, String status) {
        if (deployment != null && deployment.getErrorMessage() != null && !deployment.getErrorMessage().isBlank()) {
            return deployment.getErrorMessage();
        }
        if (status == null || status.isBlank() || "RUNNING".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
            return "Deployment did not reach a terminal state within 10 minutes";
        }
        return "Deployment failed";
    }

    private static Instant started(DeploymentView deployment) {
        if (deployment == null || deployment.getStartedAt() == null) {
            return Instant.now();
        }
        return deployment.getStartedAt();
    }

    static String kubernetesLog(DeploymentView deployment) {
        String name = deployment.getK8sDeploymentName() == null ? "" : deployment.getK8sDeploymentName();
        String revision = deployment.getK8sRevision() == null ? "" : String.valueOf(deployment.getK8sRevision());
        return "k8sDeploymentName=" + name + " revision=" + revision;
    }

    private static PipelineStepEntity requireStep(PipelineEntity pipeline, String name) {
        PipelineStepEntity step = findStep(pipeline, name);
        if (step == null) {
            throw ApiException.notFound("Pipeline step " + name + " was not found");
        }
        return step;
    }

    private static PipelineStepEntity findStep(PipelineEntity pipeline, String name) {
        for (PipelineStepEntity step : pipeline.getSteps()) {
            if (name.equals(step.getName())) {
                return step;
            }
        }
        return null;
    }

    private static List<PipelineStepEntity> ordered(PipelineEntity pipeline) {
        List<PipelineStepEntity> steps = new ArrayList<>(pipeline.getSteps());
        steps.sort(Comparator.comparingInt(PipelineStepEntity::getStepOrder));
        return steps;
    }

    private static String append(String current, String line) {
        if (line == null || line.isBlank()) {
            return current == null ? "" : current;
        }
        if (current == null || current.isBlank()) {
            return line;
        }
        return current + "\n" + line;
    }

    private static PipelineView view(PipelineEntity pipeline) {
        List<StepView> steps = new ArrayList<>();
        for (PipelineStepEntity step : ordered(pipeline)) {
            steps.add(new StepView(step.getName(), step.getStatus(), step.getLogText(), step.getStartedAt(), step.getFinishedAt()));
        }
        return new PipelineView(
                pipeline.getId(),
                pipeline.getApplicationId(),
                pipeline.getRequestedBy(),
                pipeline.getRepositoryUrl(),
                pipeline.getBranch(),
                pipeline.getStatus(),
                pipeline.getTriggerType(),
                pipeline.getConclusion(),
                pipeline.getStartedAt(),
                pipeline.getFinishedAt(),
                steps);
    }
}
