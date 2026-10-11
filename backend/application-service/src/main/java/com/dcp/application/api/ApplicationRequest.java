package com.dcp.application.api;

public record ApplicationRequest(
        String name,
        String description,
        String gitRepositoryUrl,
        String branch,
        String environment,
        String dockerfilePath,
        String contextPath,
        Integer containerPort
) {
}
