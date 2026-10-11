package com.dcp.deployment.client;

import java.util.UUID;

public record DockerBuildRequest(
        UUID applicationId,
        UUID requestedBy,
        String repositoryUrl,
        String branch,
        String dockerfilePath,
        String contextPath,
        String imageTag
) {
}
