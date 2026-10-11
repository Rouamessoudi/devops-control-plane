package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;

import java.util.ArrayList;
import java.util.List;

final class GithubAllowlist {

    private GithubAllowlist() {
    }

    static void check(String repositoryUrl, boolean enforce, String allowedOrgs) {
        String organization = ResourcePolicies.organization(repositoryUrl);
        List<String> allowed = parse(allowedOrgs);
        if (enforce && allowed.isEmpty()) {
            throw ApiException.forbidden("GitHub organization allowlist is enforced but empty");
        }
        if (!allowed.isEmpty() && allowed.stream().noneMatch(organization::equals)) {
            throw ApiException.forbidden("GitHub organization is not allowed");
        }
    }

    private static List<String> parse(String allowedOrgs) {
        List<String> allowed = new ArrayList<>();
        if (allowedOrgs == null || allowedOrgs.isBlank()) {
            return allowed;
        }
        for (String part : allowedOrgs.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                allowed.add(trimmed);
            }
        }
        return allowed;
    }
}
