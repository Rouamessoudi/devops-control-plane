package com.dcp.kubernetes;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dcp.kubernetes")
public class KubernetesProperties {

    private String namespace = "dcp";
    private int maxReplicas = 5;
    private String allowedRegistries = "localhost:15000,registry:5000";
    private String gatewayName = "dcp";
    private int rollbackMaxRestarts = 3;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getMaxReplicas() {
        return maxReplicas;
    }

    public void setMaxReplicas(int maxReplicas) {
        this.maxReplicas = maxReplicas;
    }

    public String getAllowedRegistries() {
        return allowedRegistries;
    }

    public void setAllowedRegistries(String allowedRegistries) {
        this.allowedRegistries = allowedRegistries;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public int getRollbackMaxRestarts() {
        return rollbackMaxRestarts;
    }

    public void setRollbackMaxRestarts(int rollbackMaxRestarts) {
        this.rollbackMaxRestarts = rollbackMaxRestarts;
    }
}
