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
    public static class DeploymentView {
        private UUID id;
        private UUID applicationId;
        private UUID requestedBy;
        private String imageRepository;
        private String imageTag;
        private String fullImage;
        private Integer replicas;
        private String environment;
        private String trigger;
        private String status;
        private String k8sDeploymentName;
        private Object k8sRevision;
        private String errorMessage;
        private Instant createdAt;
        private Instant startedAt;
        private Instant finishedAt;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public UUID getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(UUID applicationId) {
            this.applicationId = applicationId;
        }

        public UUID getRequestedBy() {
            return requestedBy;
        }

        public void setRequestedBy(UUID requestedBy) {
            this.requestedBy = requestedBy;
        }

        public String getImageRepository() {
            return imageRepository;
        }

        public void setImageRepository(String imageRepository) {
            this.imageRepository = imageRepository;
        }

        public String getImageTag() {
            return imageTag;
        }

        public void setImageTag(String imageTag) {
            this.imageTag = imageTag;
        }

        public String getFullImage() {
            return fullImage;
        }

        public void setFullImage(String fullImage) {
            this.fullImage = fullImage;
        }

        public Integer getReplicas() {
            return replicas;
        }

        public void setReplicas(Integer replicas) {
            this.replicas = replicas;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getTrigger() {
            return trigger;
        }

        public void setTrigger(String trigger) {
            this.trigger = trigger;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getK8sDeploymentName() {
            return k8sDeploymentName;
        }

        public void setK8sDeploymentName(String k8sDeploymentName) {
            this.k8sDeploymentName = k8sDeploymentName;
        }

        public Object getK8sRevision() {
            return k8sRevision;
        }

        public void setK8sRevision(Object k8sRevision) {
            this.k8sRevision = k8sRevision;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(Instant startedAt) {
            this.startedAt = startedAt;
        }

        public Instant getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
        }
    }
}
