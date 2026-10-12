package com.dcp.pipeline.api;

public record QualityView(
        boolean available,
        String mode,
        String projectKey,
        String qualityGate,
        Double coverage,
        Integer bugs,
        Integer vulnerabilities,
        Integer codeSmells,
        Double duplications,
        String analyzedAt,
        String url,
        String message) {
}
