package com.dcp.monitoring.prometheus;

import com.dcp.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrometheusClientTest {

    @Test
    void queryUsesTheServerCatalogAndParsesTheSample() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prometheus:9090");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String fixture = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"value\":[1710000000,\"0.25\"]}]}}";
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/query")))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));
        PrometheusClient client = new PrometheusClient(builder.build(), "http://prometheus:9090");

        Double value = client.instant(new PromqlCatalog("dcp").cpu());

        assertEquals(0.25d, value);
        server.verify();
    }

    @Test
    void httpFailureIsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prometheus:9090");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/query_range")))
                .andRespond(withServerError().body("prometheus down"));
        PrometheusClient client = new PrometheusClient(builder.build(), "http://prometheus:9090");

        ApiException exception = assertThrows(ApiException.class,
                () -> client.range("sum(jvm_memory_used_bytes{area=\"heap\"})", Duration.ofMinutes(15), 60));

        assertEquals(503, exception.status());
        server.verify();
    }
}
