package com.dcp.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

public final class MonitoringProperties {

    private MonitoringProperties() {
    }

    @ConfigurationProperties(prefix = "dcp.prometheus")
    public static class PrometheusProperties {
        private String url = "http://prometheus:9090";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    @ConfigurationProperties(prefix = "dcp.grafana")
    public static class GrafanaProperties {
        private String url = "http://localhost:13001";
        private String dashboardPath = "/d/dcp-overview";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDashboardPath() {
            return dashboardPath;
        }

        public void setDashboardPath(String dashboardPath) {
            this.dashboardPath = dashboardPath;
        }
    }

    @ConfigurationProperties(prefix = "dcp.kubernetes")
    public static class KubernetesProperties {
        private String namespace = "dcp";

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }

    @ConfigurationProperties(prefix = "dcp.clients")
    public static class ServiceClientProperties {
        private String kubernetesUrl = "http://kubernetes-service:8084";

        public String getKubernetesUrl() {
            return kubernetesUrl;
        }

        public void setKubernetesUrl(String kubernetesUrl) {
            this.kubernetesUrl = kubernetesUrl;
        }
    }
}
