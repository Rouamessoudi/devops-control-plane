package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class BuildStore {

    private final DockerBuildRepository builds;
    private final DockerBuildStepRepository steps;
    private final ManagedImageRepository images;

    public BuildStore(DockerBuildRepository builds, DockerBuildStepRepository steps, ManagedImageRepository images) {
        this.builds = builds;
        this.steps = steps;
        this.images = images;
    }

    @Transactional
    public DockerBuild insert(DockerBuild build) {
        return builds.save(build);
    }

    @Transactional(readOnly = true)
    public DockerBuild require(UUID id) {
        return builds.findById(id).orElseThrow(() -> ApiException.notFound("Build not found"));
    }

    @Transactional
    public void markRunning(UUID id) {
        DockerBuild build = require(id);
        build.setStatus("RUNNING");
        builds.save(build);
    }

    @Transactional
    public void addStep(UUID buildId, int order, String name, String status, String log) {
        String stepLog = Texts.cap(log);
        DockerBuildStep step = new DockerBuildStep();
        step.setId(UUID.randomUUID());
        step.setBuildId(buildId);
        step.setStepOrder(order);
        step.setName(name);
        step.setStatus(status);
        step.setLogText(stepLog);
        steps.save(step);

        DockerBuild build = require(buildId);
        String combined = (build.getLogText() == null ? "" : build.getLogText())
                + "\n== " + name + " " + status + " ==\n"
                + stepLog;
        build.setLogText(Texts.cap(combined));
        if ("FAILED".equals(status)) {
            String message = stepLog.isBlank() ? name + " failed" : stepLog;
            build.setErrorMessage(Texts.clip(message, 1000));
            build.setStatus("FAILED");
        }
        builds.save(build);
    }

    @Transactional
    public void recordImage(UUID buildId, String repository, String tag, String dockerImageId, Long sizeBytes) {
        ManagedImage image = new ManagedImage();
        image.setId(UUID.randomUUID());
        image.setBuildId(buildId);
        image.setRepository(repository);
        image.setTag(tag);
        image.setDockerImageId(dockerImageId);
        image.setSizeBytes(sizeBytes);
        image.setCreatedAt(Instant.now());
        images.save(image);
    }

    @Transactional
    public void finish(UUID id) {
        DockerBuild build = require(id);
        boolean failed = steps.findByBuildIdOrderByStepOrderAsc(id).stream()
                .anyMatch(step -> "FAILED".equals(step.getStatus()));
        build.setStatus(failed ? "FAILED" : "SUCCEEDED");
        build.setFinishedAt(Instant.now());
        if (!failed) {
            build.setErrorMessage(null);
        }
        builds.save(build);
    }

    @Transactional
    public void fail(UUID id, String message) {
        DockerBuild build = require(id);
        build.setStatus("FAILED");
        build.setErrorMessage(Texts.clip(message == null || message.isBlank() ? "Build failed" : message, 1000));
        build.setFinishedAt(Instant.now());
        String combined = (build.getLogText() == null ? "" : build.getLogText()) + "\n" + build.getErrorMessage();
        build.setLogText(Texts.cap(combined));
        builds.save(build);
    }
}
