package com.dcp.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dcp.sonar")
public class SonarProperties {

    private String mode = "off";
    private String url = "";
    private String projectKey = "devops-control-plane";
    private String token = "";
    private String publicUrl = "http://127.0.0.1:9000";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String publicUrl() {
        if (publicUrl == null || publicUrl.isBlank()) {
            return "http://127.0.0.1:9000";
        }
        String value = publicUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public String mode() {
        if (mode == null || mode.isBlank()) {
            return "off";
        }
        String value = mode.trim().toLowerCase();
        if ("optional".equals(value) || "required".equals(value)) {
            return value;
        }
        return "off";
    }
}
