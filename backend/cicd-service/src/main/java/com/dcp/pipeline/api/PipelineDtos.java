package com.dcp.pipeline.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PipelineDtos {

    private PipelineDtos() {
    }

    public record CreatePipelineRequest(@NotNull UUID applicationId) {
    }

    public record StepView(String name, String status, String log, Instant startedAt, Instant finishedAt) {
    }

    public record PipelineView(
            UUID id,
            UUID applicationId,
            UUID requestedBy,
            String repositoryUrl,
            String branch,
            String status,
            String trigger,
            String conclusion,
            Instant startedAt,
            Instant finishedAt,
            List<StepView> steps) {
    }

    public record PipelineAuditEvent(
            UUID id,
            String action,
            UUID applicationId,
            UUID actorId,
            String status,
            String trigger,
            String conclusion,
            Instant at) {
    }

    public record DeploymentCommand(UUID applicationId, UUID requestedBy, int replicas, String trigger) {
    }

    public record NotificationCommand(UUID userId, String title, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RemoteApplication {
        private UUID id;
        private UUID ownerId;
        @JsonAlias("repositoryUrl")
        private String gitRepositoryUrl;
        private String branch;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public UUID getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(UUID ownerId) {
            this.ownerId = ownerId;
        }

        public String getGitRepositoryUrl() {
            return gitRepositoryUrl;
        }

        public void setGitRepositoryUrl(String gitRepositoryUrl) {
            this.gitRepositoryUrl = gitRepositoryUrl;
        }

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeploymentView(
            UUID id,
            UUID applicationId,
            UUID requestedBy,
            String imageRepository,
            String imageTag,
            String fullImage,
            Integer replicas,
            String environment,
            String trigger,
            String status,
            String k8sDeploymentName,
            Object k8sRevision,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
    }
}
