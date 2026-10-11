package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

final class ImageNames {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private ImageNames() {
    }

    static String repository(String applicationName) {
        return "dcp/" + ResourcePolicies.appName(applicationName);
    }

    static String tag(String requested) {
        if (requested == null || requested.isBlank()) {
            return ResourcePolicies.imageTag(TIMESTAMP.format(Instant.now()));
        }
        return ResourcePolicies.imageTag(requested);
    }

    static String imageName(String registry, String repository) {
        String owned = requireRepository(repository);
        String prefix = normalizeRegistry(registry);
        if (prefix.isEmpty()) {
            return owned;
        }
        return prefix + "/" + owned;
    }

    static String fullImage(String registry, String repository, String tag) {
        String safeTag = ResourcePolicies.imageTag(tag);
        return imageName(registry, repository) + ":" + safeTag;
    }

    static void requireOwned(String fullImage) {
        if (fullImage == null || fullImage.isBlank() || fullImage.indexOf(' ') >= 0 || fullImage.indexOf('@') >= 0 || fullImage.contains("..")) {
            throw ApiException.badRequest("Image is not allowed");
        }
        int slash = fullImage.lastIndexOf('/');
        int colon = fullImage.lastIndexOf(':');
        if (colon <= slash || colon == fullImage.length() - 1) {
            throw ApiException.badRequest("Image is not allowed");
        }
        String repository = fullImage.substring(0, colon);
        int dcp = repository.lastIndexOf("dcp/");
        if (dcp < 0) {
            throw ApiException.badRequest("Image is not allowed");
        }
        if (dcp > 0 && repository.charAt(dcp - 1) != '/') {
            throw ApiException.badRequest("Image is not allowed");
        }
        requireRepository(repository.substring(dcp));
        ResourcePolicies.imageTag(fullImage.substring(colon + 1));
    }

    private static String requireRepository(String repository) {
        if (repository == null || !repository.startsWith("dcp/")) {
            throw ApiException.badRequest("Image repository must be dcp/{app}");
        }
        String name = repository.substring("dcp/".length());
        if (name.indexOf('/') >= 0) {
            throw ApiException.badRequest("Image repository must be dcp/{app}");
        }
        ResourcePolicies.appName(name);
        return "dcp/" + name;
    }

    static String normalizeRegistry(String registry) {
        if (registry == null) {
            return "";
        }
        String value = registry.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return "";
        }
        if (value.indexOf('/') >= 0 || value.indexOf(' ') >= 0 || value.indexOf('@') >= 0
                || value.indexOf('\\') >= 0 || value.contains("..") || value.contains("://")) {
            throw ApiException.badRequest("Registry must be a host and optional port");
        }
        return value;
    }
}
