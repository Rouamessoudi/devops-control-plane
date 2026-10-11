package com.dcp.common.policy;

import com.dcp.common.error.ApiException;

import java.util.regex.Pattern;

public final class ResourcePolicies {

    public static final Pattern GITHUB_REPOSITORY = Pattern.compile(
            "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?/?$");
    public static final Pattern BRANCH = Pattern.compile("^[A-Za-z0-9._/-]{1,128}$");
    public static final Pattern APP_NAME = Pattern.compile("^[a-z][a-z0-9-]{1,38}$");
    public static final Pattern IMAGE_TAG = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    public static final Pattern ENV_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    public static final Pattern DNS_LABEL = Pattern.compile("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$");
    public static final Pattern RELATIVE_PATH = Pattern.compile("^[A-Za-z0-9_./-]{1,200}$");

    private ResourcePolicies() {
    }

    public static String githubRepository(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!GITHUB_REPOSITORY.matcher(normalized).matches() || normalized.contains("@") || normalized.contains("..")) {
            throw ApiException.badRequest("Only https://github.com/{owner}/{repo} repositories are accepted");
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String branch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!BRANCH.matcher(normalized).matches() || normalized.contains("..")) {
            throw ApiException.badRequest("Branch name is not allowed");
        }
        return normalized;
    }

    public static String appName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!APP_NAME.matcher(normalized).matches()) {
            throw ApiException.badRequest("Application name must match ^[a-z][a-z0-9-]{1,38}$");
        }
        return normalized;
    }

    public static String imageTag(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!IMAGE_TAG.matcher(normalized).matches()) {
            throw ApiException.badRequest("Image tag contains unsupported characters");
        }
        return normalized;
    }

    public static String envKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!ENV_KEY.matcher(normalized).matches()) {
            throw ApiException.badRequest("Environment variable name is not allowed");
        }
        return normalized;
    }

    public static String dnsLabel(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (!DNS_LABEL.matcher(normalized).matches()) {
            throw ApiException.badRequest(field + " is not a DNS label");
        }
        return normalized;
    }

    public static String relativePath(String value, String fallback) {
        String normalized = (value == null || value.isBlank()) ? fallback : value.trim();
        if (normalized.contains("..") || normalized.startsWith("/") || normalized.contains("\\")
                || !RELATIVE_PATH.matcher(normalized).matches()) {
            throw ApiException.badRequest("Path must be relative and must not contain '..'");
        }
        return normalized;
    }

    public static void replicas(int replicas, int max) {
        if (replicas < 1 || replicas > max) {
            throw ApiException.badRequest("Replicas must be between 1 and " + max);
        }
    }

    public static String organization(String repositoryUrl) {
        String trimmed = githubRepository(repositoryUrl);
        String path = trimmed.substring("https://github.com/".length());
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        return path.substring(0, path.indexOf('/'));
    }
}
