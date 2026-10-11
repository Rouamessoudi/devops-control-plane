package com.dcp.pipeline.github;

import com.dcp.common.policy.ResourcePolicies;

public record GithubCoordinates(String owner, String repository) {

    public static GithubCoordinates parse(String repositoryUrl) {
        String normalized = ResourcePolicies.githubRepository(repositoryUrl);
        String owner = ResourcePolicies.organization(normalized);
        String path = normalized.substring("https://github.com/".length());
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        String repository = path.substring(path.indexOf('/') + 1);
        return new GithubCoordinates(owner, repository);
    }
}
