package com.dcp.common.web;

import com.dcp.common.security.InternalTokenProperties;
import com.dcp.common.security.JwtService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class WebAutoConfiguration {

    @Bean
    public RestExceptionHandler restExceptionHandler() {
        return new RestExceptionHandler();
    }

    @Bean
    public BearerTokenFilter bearerTokenFilter(JwtService jwtService) {
        return new BearerTokenFilter(jwtService);
    }

    @Bean
    public InternalTokenFilter internalTokenFilter(InternalTokenProperties properties) {
        return new InternalTokenFilter(properties);
    }
}
