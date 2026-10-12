package com.dcp.pipeline.config;

import com.dcp.common.security.InternalTokenProperties;
import com.dcp.common.web.InternalRestClient;
import com.dcp.pipeline.github.GithubWorkflowClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties({PipelineProperties.class, GithubProperties.class, ServiceClientProperties.class, SonarProperties.class})
public class PipelineConfiguration {

    @Bean
    public InternalRestClient applicationClient(ServiceClientProperties properties, InternalTokenProperties tokens) {
        return InternalRestClient.from(properties.getApplicationUrl(), tokens, "cicd-application");
    }

    @Bean
    public InternalRestClient deploymentClient(ServiceClientProperties properties, InternalTokenProperties tokens) {
        return InternalRestClient.from(properties.getDeploymentUrl(), tokens, "cicd-deployment");
    }

    @Bean
    public InternalRestClient notificationClient(ServiceClientProperties properties, InternalTokenProperties tokens) {
        return InternalRestClient.from(properties.getAuthUrl(), tokens, "cicd-auth");
    }

    @Bean
    public GithubWorkflowClient githubWorkflowClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(20));
        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(factory)
                .build();
        return new GithubWorkflowClient(restClient);
    }

    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("pipeline-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
