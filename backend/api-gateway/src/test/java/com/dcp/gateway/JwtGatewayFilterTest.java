package com.dcp.gateway;

import com.dcp.common.security.JwtProperties;
import com.dcp.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtGatewayFilterTest {

    private final JwtService jwtService = jwtService();
    private final JwtGatewayFilter filter = new JwtGatewayFilter(jwtService);

    @Test
    void publicLoginDoesNotRequireATokenAndDropsTheInternalHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Internal-Token", "browser-supplied")
                        .build());
        AtomicReference<String> forwarded = new AtomicReference<>("present");
        GatewayFilterChain chain = seen -> {
            forwarded.set(seen.getRequest().getHeaders().getFirst("X-Internal-Token"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNull(forwarded.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedRouteWithoutBearerIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/applications").build());

        filter.filter(exchange, seen -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validBearerIsForwarded() {
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "dev@dcp.local", Set.of("DEVELOPER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/applications").header("Authorization", "Bearer " + token).build());
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        filter.filter(exchange, seen -> {
            called.set(true);
            return Mono.empty();
        }).block();

        assertEquals(true, called.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    private static JwtService jwtService() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("dev-only-jwt-secret-change-me-32b");
        properties.setAccessTokenMinutes(15);
        return new JwtService(properties);
    }
}
