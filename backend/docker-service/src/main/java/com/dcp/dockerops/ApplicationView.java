package com.dcp.dockerops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicationView(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String gitRepositoryUrl,
        String branch,
        String environment,
        String dockerfilePath,
        String contextPath,
        Integer containerPort,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
