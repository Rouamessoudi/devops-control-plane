package com.dcp.application.config;

import com.dcp.common.crypto.SecretBox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    @Bean
    public SecretBox secretBox(@Value("${dcp.crypto.secret}") String secret) {
        return new SecretBox(secret);
    }
}
