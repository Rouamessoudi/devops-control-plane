package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.dcp.common.web.InternalRestClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class BuildService {

    private final BuildStore store;
    private final BuildJob job;
    private final InternalRestClient applicationClient;
    private final DockerProperties dockerProperties;
    private final GithubProperties githubProperties;
    private final Executor dockerBuildExecutor;

    public BuildService(BuildStore store,
                        BuildJob job,
                        InternalRestClient applicationClient,
                        DockerProperties dockerProperties,
                        GithubProperties githubProperties,
                        @Qualifier("dockerBuildExecutor") Executor dockerBuildExecutor) {
        this.store = store;
        this.job = job;
        this.applicationClient = applicationClient;
        this.dockerProperties = dockerProperties;
        this.githubProperties = githubProperties;
        this.dockerBuildExecutor = dockerBuildExecutor;
    }

    public DockerApi.BuildAccepted acceptFromUser(DockerApi.UserBuildRequest request) {
        ApplicationView application = loadApplication(request.applicationId());
        JwtPrincipal user = CurrentUser.require();
        if (!user.userId().equals(application.ownerId()) && !user.hasRole("ADMIN")) {
            throw ApiException.forbidden("You do not own this application");
        }
        return accept(new DockerApi.BuildRequest(
                application.id(),
                user.userId(),
                application.gitRepositoryUrl(),
                application.branch(),
                application.dockerfilePath(),
                application.contextPath(),
                request.imageTag()
        ), application.name());
    }

    public DockerApi.BuildAccepted acceptInternal(DockerApi.BuildRequest request) {
        ApplicationView application = loadApplication(request.applicationId());
        String repository = request.repositoryUrl() == null || request.repositoryUrl().isBlank()
                ? application.gitRepositoryUrl() : request.repositoryUrl();
        String branch = request.branch() == null || request.branch().isBlank()
                ? application.branch() : request.branch();
        String dockerfile = request.dockerfilePath() == null || request.dockerfilePath().isBlank()
                ? application.dockerfilePath() : request.dockerfilePath();
        String context = request.contextPath() == null || request.contextPath().isBlank()
                ? application.contextPath() : request.contextPath();
        return accept(new DockerApi.BuildRequest(
                request.applicationId(),
                request.requestedBy(),
                repository,
                branch,
                dockerfile,
                context,
                request.imageTag()
        ), application.name());
    }

    private DockerApi.BuildAccepted accept(DockerApi.BuildRequest request, String applicationName) {
        String repositoryUrl = ResourcePolicies.githubRepository(request.repositoryUrl());
        GithubAllowlist.check(repositoryUrl, githubProperties.isEnforceAllowlist(), githubProperties.getAllowedOrgs());
        String branch = ResourcePolicies.branch(request.branch());
        String dockerfilePath = ResourcePolicies.relativePath(request.dockerfilePath(), "Dockerfile");
        String contextPath = ResourcePolicies.relativePath(request.contextPath(), ".");
        String repository = ImageNames.repository(applicationName);
        String tag = ImageNames.tag(request.imageTag());
        String imageName = ImageNames.imageName(dockerProperties.getPublicRegistry(), repository);
        String fullImage = ImageNames.fullImage(dockerProperties.getPublicRegistry(), repository, tag);
        String pushImage = ImageNames.fullImage(dockerProperties.getRegistry(), repository, tag);
        ImageNames.requireOwned(fullImage);
        ImageNames.requireOwned(pushImage);

        DockerBuild build = new DockerBuild();
        build.setId(UUID.randomUUID());
        build.setApplicationId(request.applicationId());
        build.setRequestedBy(request.requestedBy());
        build.setRepositoryUrl(repositoryUrl);
        build.setBranch(branch);
        build.setImageName(imageName);
        build.setImageTag(tag);
        build.setFullImage(fullImage);
        build.setStatus("PENDING");
        build.setStartedAt(Instant.now());
        DockerBuild saved = store.insert(build);
        try {
            dockerBuildExecutor.execute(() -> job.run(saved.getId(), dockerfilePath, contextPath, pushImage));
        } catch (RejectedExecutionException ex) {
            store.fail(saved.getId(), "Build executor is saturated");
            throw ApiException.unavailable("Build executor is saturated");
        }
        return new DockerApi.BuildAccepted(saved.getId(), "PENDING", imageName, tag, fullImage);
    }

    private ApplicationView loadApplication(UUID applicationId) {
        if (applicationId == null) {
            throw ApiException.badRequest("applicationId is required");
        }
        return applicationClient.get("/internal/applications/" + applicationId, ApplicationView.class);
    }
}
