package com.dcp.deployment.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BuildView(
        UUID id,
        UUID applicationId,
        String status,
        String imageName,
        String imageTag,
        String fullImage,
        String logText,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        List<Object> steps
) {
}
