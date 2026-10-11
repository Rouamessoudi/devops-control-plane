package com.dcp.gateway;

import com.dcp.common.security.JwtService;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;

    public JwtGatewayFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var cleaned = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.remove("X-Internal-Token")))
                .build();
        String path = cleaned.getRequest().getPath().pathWithinApplication().value();
        String method = cleaned.getRequest().getMethod() == null ? "" : cleaned.getRequest().getMethod().name();
        if (PublicPaths.isPublic(method, path)) {
            return chain.filter(cleaned);
        }
        String header = cleaned.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(cleaned);
        }
        try {
            jwtService.parse(header.substring("Bearer ".length()).trim());
        } catch (RuntimeException ex) {
            return unauthorized(cleaned);
        }
        return chain.filter(cleaned);
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Authentication required\"}"
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
