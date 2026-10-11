package com.dcp.common.security;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties({JwtProperties.class, InternalTokenProperties.class})
public class DcpSecurityAutoConfiguration {

    @Bean
    public JwtService jwtService(JwtProperties properties) {
        propertiesCheck(properties);
        return new JwtService(properties);
    }

    @Bean
    public ApplicationRunner productionSecretGuard(Environment environment,
                                                    JwtProperties jwtProperties,
                                                    InternalTokenProperties internalTokenProperties) {
        return args -> JwtService.rejectDevSecretsInProduction(
                environment.getActiveProfiles(),
                jwtProperties.getSecret(),
                internalTokenProperties.getInternalToken());
    }

    private static void propertiesCheck(JwtProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().length() < 32) {
            throw new IllegalStateException("dcp.jwt.secret must contain at least 32 characters");
        }
    }
}
