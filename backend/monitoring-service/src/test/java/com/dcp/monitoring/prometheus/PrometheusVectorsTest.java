package com.dcp.monitoring.prometheus;

import com.dcp.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusVectorsTest {

    @Test
    void parsesInstantSample() {
        String json = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"value\":[1710000000,\"0.25\"]}]}}";
        assertEquals(0.25d, PrometheusVectors.parseInstant(json));
    }

    @Test
    void emptyResultReturnsNull() {
        String json = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";
        assertNull(PrometheusVectors.parseInstant(json));
    }

    @Test
    void statusErrorThrows() {
        String json = "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"parse error\"}";
        ApiException exception = assertThrows(ApiException.class, () -> PrometheusVectors.parseInstant(json));
        assertEquals(503, exception.status());
        assertTrue(exception.getMessage().contains("parse error"));
    }

    @Test
    void emptyRangeReturnsNoPoints() {
        String json = "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}";
        assertTrue(PrometheusVectors.parseRange(json).timestamps().isEmpty());
        assertTrue(PrometheusVectors.parseRange(json).values().isEmpty());
    }
}
