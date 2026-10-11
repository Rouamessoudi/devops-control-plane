package com.dcp.deployment.config;

import com.dcp.common.security.InternalTokenProperties;
import com.dcp.common.web.InternalRestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ClientConfig {

    @Bean
    public InternalRestClient applicationClient(@Value("${dcp.clients.application-url}") String url,
                                                 InternalTokenProperties properties) {
        return InternalRestClient.from(url, properties, "application");
    }

    @Bean
    public InternalRestClient dockerClient(@Value("${dcp.clients.docker-url}") String url,
                                            InternalTokenProperties properties) {
        return InternalRestClient.from(url, properties, "docker");
    }

    @Bean
    public InternalRestClient kubernetesClient(@Value("${dcp.clients.kubernetes-url}") String url,
                                                InternalTokenProperties properties) {
        return InternalRestClient.from(url, properties, "kubernetes");
    }

    @Bean
    public InternalRestClient authClient(@Value("${dcp.clients.auth-url}") String url,
                                          InternalTokenProperties properties) {
        return InternalRestClient.from(url, properties, "auth");
    }

    @Bean
    public InternalRestClient monitoringClient(@Value("${dcp.clients.monitoring-url}") String url,
                                                InternalTokenProperties properties) {
        return InternalRestClient.from(url, properties, "monitoring");
    }

    @Bean(name = "deploymentExecutor")
    public Executor deploymentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("deployment-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }
}
