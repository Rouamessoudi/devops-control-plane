package com.dcp.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dcp.pipeline")
public class PipelineProperties {

    private String mode = "local";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean local() {
        return mode == null || mode.isBlank() || "local".equalsIgnoreCase(mode.trim());
    }

    public boolean github() {
        return mode != null && "github".equalsIgnoreCase(mode.trim());
    }
}
