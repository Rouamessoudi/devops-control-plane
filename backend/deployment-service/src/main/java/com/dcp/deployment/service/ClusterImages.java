package com.dcp.deployment.service;

final class ClusterImages {

    private ClusterImages() {
    }

    static String forCluster(String fullImage, String publicRegistry, String clusterRegistry) {
        if (fullImage == null || fullImage.isBlank()) {
            return fullImage;
        }
        String source = trim(publicRegistry);
        String target = trim(clusterRegistry);
        if (source.isEmpty() || target.isEmpty() || source.equals(target)) {
            return fullImage;
        }
        String prefix = source + "/";
        if (!fullImage.startsWith(prefix)) {
            return fullImage;
        }
        return target + fullImage.substring(source.length());
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
