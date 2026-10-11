package com.dcp.common.web;

import com.dcp.common.error.ApiException;
import com.dcp.common.security.InternalTokenProperties;
import com.dcp.common.security.JwtService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.function.Supplier;

public class InternalRestClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public InternalRestClient(String baseUrl, String internalToken, String name) {
        this.restClient = RestClient.builder()
                .baseUrl(trim(baseUrl))
                .defaultHeader(InternalTokenFilter.HEADER, internalToken)
                .build();
        this.circuitBreaker = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .ignoreExceptions(ApiException.class)
                .build());
        this.retry = Retry.of(name, RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(200))
                .retryExceptions(ResourceAccessException.class)
                .build());
    }

    public static InternalRestClient from(String baseUrl, InternalTokenProperties properties, String name) {
        return new InternalRestClient(baseUrl, properties.getInternalToken(), name);
    }

    public <T> T get(String path, Class<T> type) {
        return call(() -> restClient.get().uri(path).retrieve().body(type));
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return call(() -> restClient.post().uri(path).body(body).retrieve().body(type));
    }

    public void post(String path, Object body) {
        call(() -> {
            restClient.post().uri(path).body(body).retrieve().toBodilessEntity();
            return Boolean.TRUE;
        });
    }

    public <T> T patch(String path, Object body, Class<T> type) {
        return call(() -> restClient.patch().uri(path).body(body).retrieve().body(type));
    }

    private <T> T call(Supplier<T> supplier) {
        Supplier<T> decorated = Retry.decorateSupplier(retry, () -> {
            try {
                return supplier.get();
            } catch (RestClientResponseException ex) {
                throw map(ex);
            }
        });
        try {
            return circuitBreaker.executeSupplier(decorated::get);
        } catch (CallNotPermittedException ex) {
            throw ApiException.unavailable("Downstream service is temporarily unavailable");
        }
    }

    private static ApiException map(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String detail = exception.getResponseBodyAsString();
        if (detail == null || detail.isBlank()) {
            detail = exception.getStatusText();
        }
        if (detail.length() > 500) {
            detail = detail.substring(0, 500);
        }
        if (status.value() == 400) {
            return ApiException.badRequest(detail);
        }
        if (status.value() == 404) {
            return ApiException.notFound(detail);
        }
        if (status.value() == 409) {
            return ApiException.conflict(detail);
        }
        if (status.value() == 401 || status.value() == 403) {
            return ApiException.forbidden("Downstream rejected the service call");
        }
        return ApiException.unavailable(detail);
    }

    private static String trim(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
