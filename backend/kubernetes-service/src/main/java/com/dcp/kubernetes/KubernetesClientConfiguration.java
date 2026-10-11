package com.dcp.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;

@Configuration
public class KubernetesClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KubernetesClientConfiguration.class);

    @Bean(destroyMethod = "close")
    KubernetesClient kubernetesClient() {
        try {
            Config config = new ConfigBuilder(Config.autoConfigure(null))
                    .withConnectionTimeout(3000)
                    .withRequestTimeout(5000)
                    .build();
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (RuntimeException ex) {
            log.warn("Kubernetes client could not be created ({})", ex.getClass().getSimpleName());
            return unavailable();
        }
    }

    private static KubernetesClient unavailable() {
        return (KubernetesClient) Proxy.newProxyInstance(
                KubernetesClient.class.getClassLoader(),
                new Class<?>[]{KubernetesClient.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "unavailable-kubernetes-client";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw com.dcp.common.error.ApiException.unavailable("Kubernetes API is unavailable");
                });
    }
}
