package com.dcp.dockerops;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DockerApi {

    private DockerApi() {
    }

    public record UserBuildRequest(@NotNull UUID applicationId, String imageTag) {
    }

    public record BuildRequest(
            @NotNull UUID applicationId,
            @NotNull UUID requestedBy,
            String repositoryUrl,
            String branch,
            String dockerfilePath,
            String contextPath,
            String imageTag
    ) {
    }

    public record BuildAccepted(UUID id, String status, String imageName, String imageTag, String fullImage) {
    }

    public record StepView(String name, String status, String log) {
    }

    public record BuildView(
            UUID id,
            UUID applicationId,
            String status,
            String imageName,
            String imageTag,
            String fullImage,
            String logText,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            List<StepView> steps
    ) {
    }

    public record BuildAuditEvent(
            UUID id,
            UUID applicationId,
            UUID requestedBy,
            String repositoryUrl,
            String branch,
            String imageName,
            String imageTag,
            String fullImage,
            String status,
            String logText,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    public record ImageRow(
            UUID id,
            UUID buildId,
            String repository,
            String tag,
            String dockerImageId,
            Long sizeBytes,
            Instant createdAt,
            String source
    ) {
    }

    public record ImageListing(List<ImageRow> images, String warning) {
    }

    public record ContainerView(
            String id,
            String image,
            String state,
            String status,
            List<String> names,
            Instant createdAt
    ) {
    }
}
