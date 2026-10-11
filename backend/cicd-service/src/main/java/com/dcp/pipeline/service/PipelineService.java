package com.dcp.pipeline.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.dcp.common.web.InternalRestClient;
import com.dcp.pipeline.api.PipelineDtos.PipelineView;
import com.dcp.pipeline.api.PipelineDtos.RemoteApplication;
import com.dcp.pipeline.config.GithubProperties;
import com.dcp.pipeline.config.PipelineProperties;
import com.dcp.pipeline.github.GithubWorkflowClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class PipelineService {

    private final PipelineStore store;
    private final PipelineRunner runner;
    private final InternalRestClient applicationClient;
    private final GithubWorkflowClient github;
    private final PipelineProperties pipelineProperties;
    private final GithubProperties githubProperties;
    private final Executor pipelineExecutor;

    public PipelineService(PipelineStore store,
                           PipelineRunner runner,
                           @Qualifier("applicationClient") InternalRestClient applicationClient,
                           GithubWorkflowClient github,
                           PipelineProperties pipelineProperties,
                           GithubProperties githubProperties,
                           @Qualifier("pipelineExecutor") Executor pipelineExecutor) {
        this.store = store;
        this.runner = runner;
        this.applicationClient = applicationClient;
        this.github = github;
        this.pipelineProperties = pipelineProperties;
        this.githubProperties = githubProperties;
        this.pipelineExecutor = pipelineExecutor;
    }

    public PipelineView start(UUID applicationId) {
        JwtPrincipal user = CurrentUser.require();
        RemoteApplication application = applicationClient.get("/internal/applications/" + applicationId, RemoteApplication.class);
        if (application == null || application.getId() == null) {
            throw ApiException.notFound("Application not found");
        }
        if (!user.hasRole("ADMIN") && !user.userId().equals(application.getOwnerId())) {
            throw ApiException.forbidden("You do not own this application");
        }
        String repository = ResourcePolicies.githubRepository(application.getGitRepositoryUrl());
        String branch = ResourcePolicies.branch(application.getBranch());
        if (pipelineProperties.github()) {
            return startGithub(user.userId(), applicationId, repository, branch);
        }
        if (!pipelineProperties.local()) {
            throw ApiException.badRequest("Unsupported pipeline mode");
        }
        PipelineView created = store.create(user.userId(), applicationId, repository, branch,
                List.of(PipelineStore.PREPARE, PipelineStore.BUILD, PipelineStore.DEPLOY, PipelineStore.VERIFY));
        pipelineExecutor.execute(() -> runner.runLocal(created.id(), applicationId, user.userId()));
        return created;
    }

    private PipelineView startGithub(UUID requestedBy, UUID applicationId, String repository, String branch) {
        PipelineView created = store.create(requestedBy, applicationId, repository, branch,
                List.of(PipelineStore.DISPATCH, PipelineStore.AWAIT));
        String token = githubProperties.getToken();
        if (token == null || token.isBlank()) {
            return store.failGithub(created.id(), "GitHub token is not configured");
        }
        try {
            github.dispatch(token, repository, githubProperties.getWorkflow(), branch, created.id());
            return store.markDispatched(created.id(), githubProperties.getWorkflow(), branch);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? "GitHub workflow dispatch failed" : ex.getMessage();
            return store.failGithub(created.id(), message);
        }
    }
}
