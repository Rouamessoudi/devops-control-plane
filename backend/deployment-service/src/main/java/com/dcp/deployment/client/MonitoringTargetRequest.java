package com.dcp.deployment.client;

import java.util.UUID;

public record MonitoringTargetRequest(UUID applicationId, UUID ownerId, String deploymentName, String namespace) {
}
