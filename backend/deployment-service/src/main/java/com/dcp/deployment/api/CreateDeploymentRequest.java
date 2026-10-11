package com.dcp.deployment.api;

import java.util.UUID;

public record CreateDeploymentRequest(UUID applicationId, String imageTag, Integer replicas, String trigger) {
}
