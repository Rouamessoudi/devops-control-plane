package com.dcp.pipeline.github;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

public class GithubWorkflowClient {

    private final RestClient restClient;

    public GithubWorkflowClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void dispatch(String token, String repositoryUrl, String workflow, String branch, UUID pipelineId) {
        GithubCoordinates coordinates = GithubCoordinates.parse(repositoryUrl);
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("GitHub token is not configured");
        }
        if (workflow == null || workflow.isBlank() || workflow.contains("..") || workflow.contains("/") || workflow.contains("\\")) {
            throw ApiException.badRequest("Workflow file name is not allowed");
        }
        String ref = ResourcePolicies.branch(branch);
        Map<String, Object> body = Map.of(
                "ref", ref,
                "inputs", Map.of("pipeline_id", pipelineId.toString()));
        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches",
                            coordinates.owner(), coordinates.repository(), workflow)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.USER_AGENT, "dcp-cicd-service")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            if (detail == null || detail.isBlank()) {
                detail = ex.getStatusCode().toString();
            }
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            throw ApiException.unavailable("GitHub workflow dispatch failed: " + detail);
        }
    }
}
