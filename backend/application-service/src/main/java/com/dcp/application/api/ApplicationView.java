package com.dcp.application.api;

import com.dcp.application.domain.ApplicationStatus;
import com.dcp.application.domain.EnvironmentName;

import java.time.Instant;
import java.util.UUID;

public record ApplicationView(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String gitRepositoryUrl,
        String branch,
        EnvironmentName environment,
        String dockerfilePath,
        String contextPath,
        int containerPort,
        ApplicationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
