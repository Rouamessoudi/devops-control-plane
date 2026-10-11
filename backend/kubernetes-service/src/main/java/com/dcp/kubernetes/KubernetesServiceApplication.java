package com.dcp.kubernetes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KubernetesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KubernetesServiceApplication.class, args);
    }
}
