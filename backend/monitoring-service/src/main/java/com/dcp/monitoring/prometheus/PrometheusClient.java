package com.dcp.monitoring.prometheus;

import com.dcp.common.error.ApiException;
import com.dcp.monitoring.prometheus.PrometheusVectors.SeriesPoints;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public class PrometheusClient {

    private final RestClient restClient;
    private final String baseUrl;

    public PrometheusClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public Double instant(String promql) {
        String body = exchange(URI.create(baseUrl + "/api/v1/query?query=" + encode(promql)));
        return PrometheusVectors.parseInstant(body);
    }

    public SeriesPoints range(String promql, Duration duration, long stepSeconds) {
        long end = Instant.now().getEpochSecond();
        long start = end - duration.toSeconds();
        String body = exchange(URI.create(baseUrl + "/api/v1/query_range?query=" + encode(promql)
                + "&start=" + start + "&end=" + end + "&step=" + stepSeconds));
        return PrometheusVectors.parseRange(body);
    }

    private static String encode(String promql) {
        return UriUtils.encodeQueryParam(promql, StandardCharsets.UTF_8);
    }

    private String exchange(URI uri) {
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw ApiException.unavailable("Prometheus returned an empty body");
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            if (detail == null || detail.isBlank()) {
                detail = ex.getMessage();
            }
            throw ApiException.unavailable(detail == null || detail.isBlank() ? "Prometheus request failed" : trim(detail));
        } catch (RestClientException ex) {
            String detail = ex.getMessage();
            throw ApiException.unavailable(detail == null || detail.isBlank() ? "Prometheus request failed" : trim(detail));
        }
    }

    private static String trim(String detail) {
        return detail.length() > 500 ? detail.substring(0, 500) : detail;
    }
}
