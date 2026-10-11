package com.dcp.deployment.api;

import java.util.UUID;

public record InternalDeploymentRequest(
        UUID applicationId,
        UUID requestedBy,
        String imageTag,
        Integer replicas,
        String trigger,
        UUID pipelineId
) {
}
