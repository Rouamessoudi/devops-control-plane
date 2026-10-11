package com.dcp.deployment.client;

import java.util.UUID;

public record BuildAccepted(UUID id, String status, String imageName, String imageTag, String fullImage) {
}
