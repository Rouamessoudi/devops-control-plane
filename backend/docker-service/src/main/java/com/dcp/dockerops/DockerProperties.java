package com.dcp.dockerops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dcp.docker")
public class DockerProperties {

    private String registry = "";
    private String publicRegistry = "";

    public String getRegistry() {
        return registry;
    }

    public void setRegistry(String registry) {
        this.registry = registry;
    }

    public String getPublicRegistry() {
        return publicRegistry;
    }

    public void setPublicRegistry(String publicRegistry) {
        this.publicRegistry = publicRegistry;
    }
}
