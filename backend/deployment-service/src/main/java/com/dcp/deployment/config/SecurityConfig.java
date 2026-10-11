package com.dcp.deployment.config;

import com.dcp.common.web.BearerTokenFilter;
import com.dcp.common.web.HttpSecuritySupport;
import com.dcp.common.web.InternalTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    BearerTokenFilter bearer,
                                                    InternalTokenFilter internal) throws Exception {
        HttpSecuritySupport.baseline(http, bearer, internal);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/internal/**").hasRole("INTERNAL")
                .anyRequest().authenticated());
        return http.build();
    }
}
