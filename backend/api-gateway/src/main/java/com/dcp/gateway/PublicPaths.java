package com.dcp.gateway;

public final class PublicPaths {

    private PublicPaths() {
    }

    public static boolean isPublic(String method, String path) {
        if (path.startsWith("/actuator/")) {
            return true;
        }
        if (!"POST".equals(method)) {
            return false;
        }
        return "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/api/cicd/webhooks/github".equals(path);
    }
}
