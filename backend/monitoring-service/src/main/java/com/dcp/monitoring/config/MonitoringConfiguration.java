package com.dcp.monitoring.config;

import com.dcp.common.security.InternalTokenProperties;
import com.dcp.common.web.InternalRestClient;
import com.dcp.monitoring.config.MonitoringProperties.GrafanaProperties;
import com.dcp.monitoring.config.MonitoringProperties.KubernetesProperties;
import com.dcp.monitoring.config.MonitoringProperties.PrometheusProperties;
import com.dcp.monitoring.config.MonitoringProperties.ServiceClientProperties;
import com.dcp.monitoring.prometheus.PromqlCatalog;
import com.dcp.monitoring.prometheus.PrometheusClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({
        PrometheusProperties.class,
        GrafanaProperties.class,
        KubernetesProperties.class,
        ServiceClientProperties.class
})
public class MonitoringConfiguration {

    @Bean
    public PromqlCatalog promqlCatalog(KubernetesProperties properties) {
        return new PromqlCatalog(properties.getNamespace());
    }

    @Bean
    public PrometheusClient prometheusClient(PrometheusProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(8));
        String url = properties.getUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        return new PrometheusClient(restClient, url);
    }

    @Bean
    public InternalRestClient kubernetesClient(ServiceClientProperties properties, InternalTokenProperties tokens) {
        return InternalRestClient.from(properties.getKubernetesUrl(), tokens, "monitoring-kubernetes");
    }
}
