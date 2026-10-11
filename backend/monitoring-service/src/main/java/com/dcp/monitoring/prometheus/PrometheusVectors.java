package com.dcp.monitoring.prometheus;

import com.dcp.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class PrometheusVectors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PrometheusVectors() {
    }

    public static Double parseInstant(String json) {
        JsonNode result = result(json);
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode value = result.get(0).path("value");
        if (!value.isArray() || value.size() < 2 || value.get(1).isNull()) {
            return null;
        }
        return Double.parseDouble(value.get(1).asText());
    }

    public static SeriesPoints parseRange(String json) {
        JsonNode result = result(json);
        if (!result.isArray() || result.isEmpty()) {
            return SeriesPoints.empty();
        }
        JsonNode values = result.get(0).path("values");
        List<Long> timestamps = new ArrayList<>();
        List<Double> points = new ArrayList<>();
        if (values.isArray()) {
            for (JsonNode sample : values) {
                if (sample.isArray() && sample.size() >= 2 && !sample.get(1).isNull()) {
                    timestamps.add(sample.get(0).asLong());
                    points.add(Double.parseDouble(sample.get(1).asText()));
                }
            }
        }
        return new SeriesPoints(List.copyOf(timestamps), List.copyOf(points));
    }

    private static JsonNode result(String json) {
        if (json == null || json.isBlank()) {
            throw ApiException.unavailable("Prometheus returned an empty body");
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!"success".equals(root.path("status").asText())) {
                String error = root.path("error").asText("");
                if (error.isBlank()) {
                    error = "Prometheus query failed";
                }
                throw ApiException.unavailable(error);
            }
            return root.path("data").path("result");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.unavailable("Prometheus response could not be read");
        }
    }

    public record SeriesPoints(List<Long> timestamps, List<Double> values) {
        public static SeriesPoints empty() {
            return new SeriesPoints(List.of(), List.of());
        }
    }
}
