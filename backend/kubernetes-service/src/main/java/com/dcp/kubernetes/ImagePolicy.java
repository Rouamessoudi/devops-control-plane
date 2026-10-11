package com.dcp.kubernetes;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;

import java.util.ArrayList;
import java.util.List;

final class ImagePolicy {

    private ImagePolicy() {
    }

    static void require(String image, String allowedRegistries) {
        if (image == null || image.isBlank() || hasWhitespace(image) || image.indexOf('@') >= 0
                || image.contains("..") || image.indexOf('\\') >= 0) {
            throw ApiException.badRequest("Image is not allowed");
        }
        int slash = image.lastIndexOf('/');
        int colon = image.lastIndexOf(':');
        if (colon < 0 || colon < slash || colon == image.length() - 1) {
            throw ApiException.badRequest("Image is not allowed");
        }
        String repository = image.substring(0, colon);
        String tag = image.substring(colon + 1);
        ResourcePolicies.imageTag(tag);
        if (repository.startsWith("dcp/")) {
            requireName(repository.substring("dcp/".length()));
            return;
        }
        int marker = repository.indexOf("/dcp/");
        if (marker <= 0) {
            throw ApiException.badRequest("Image is not allowed");
        }
        String registry = repository.substring(0, marker);
        String name = repository.substring(marker + "/dcp/".length());
        if (registry.isBlank() || registry.indexOf('/') >= 0 || registry.indexOf(' ') >= 0 || registry.contains("..")) {
            throw ApiException.badRequest("Image is not allowed");
        }
        requireName(name);
        if (!allowed(registry, allowedRegistries)) {
            throw ApiException.badRequest("Image registry is not allowed");
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.indexOf('/') >= 0) {
            throw ApiException.badRequest("Image is not allowed");
        }
        ResourcePolicies.appName(name);
    }

    private static boolean allowed(String registry, String allowedRegistries) {
        if (allowedRegistries == null || allowedRegistries.isBlank()) {
            return false;
        }
        List<String> entries = new ArrayList<>();
        for (String part : allowedRegistries.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries.contains(registry);
    }

    private static boolean hasWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
