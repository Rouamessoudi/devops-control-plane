package com.dcp.pipeline.sonar;

import com.dcp.pipeline.api.QualityView;
import com.dcp.pipeline.config.SonarProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class SonarProbe {

    private final SonarProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public SonarProbe(SonarProperties properties) {
        this.properties = properties;
    }

    public QualityView read() {
        String key = properties.getProjectKey() == null || properties.getProjectKey().isBlank()
                ? "devops-control-plane"
                : properties.getProjectKey().trim();
        String base = properties.getUrl() == null ? "" : properties.getUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            return empty(key, false, "SonarQube URL is empty. No analysis result is available.");
        }
        try {
            HttpResponse<String> gate = send(base + "/api/qualitygates/project_status?projectKey=" + key);
            if (gate.statusCode() != 200) {
                return empty(key, true, "SonarQube answered HTTP " + gate.statusCode() + " for the quality gate. No measure is invented.");
            }
            JsonNode status = mapper.readTree(gate.body()).path("projectStatus").path("status");
            String gateStatus = status.isMissingNode() || status.isNull() ? null : status.asText();
            Measures measures = measures(base, key);
            return new QualityView(true, properties.mode(), key, gateStatus, measures.coverage, measures.bugs,
                    measures.vulnerabilities, measures.codeSmells, measures.duplications, analyzedAt(base, key),
                    dashboard(key),
                    "Quality gate read from SonarQube: " + (gateStatus == null ? "absent" : gateStatus));
        } catch (Exception ex) {
            return empty(key, false, "SonarQube is unreachable. No analysis result is available.");
        }
    }

    private QualityView empty(String key, boolean available, String message) {
        return new QualityView(available, properties.mode(), key, null, null, null, null, null, null, null,
                dashboard(key), message);
    }

    private String dashboard(String key) {
        return properties.publicUrl() + "/dashboard?id=" + key;
    }

    private Measures measures(String base, String key) {
        try {
            HttpResponse<String> response = send(base
                    + "/api/measures/component?component=" + key
                    + "&metricKeys=coverage,bugs,vulnerabilities,code_smells,duplicated_lines_density");
            if (response.statusCode() != 200) {
                return Measures.empty();
            }
            JsonNode nodes = mapper.readTree(response.body()).path("component").path("measures");
            Double coverage = null;
            Integer bugs = null;
            Integer vulnerabilities = null;
            Integer codeSmells = null;
            Double duplications = null;
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    String metric = node.path("metric").asText("");
                    String value = node.path("value").asText("");
                    if (value.isBlank()) {
                        continue;
                    }
                    try {
                        if ("coverage".equals(metric)) {
                            coverage = Double.valueOf(value);
                        } else if ("bugs".equals(metric)) {
                            bugs = Integer.valueOf(value);
                        } else if ("vulnerabilities".equals(metric)) {
                            vulnerabilities = Integer.valueOf(value);
                        } else if ("code_smells".equals(metric)) {
                            codeSmells = Integer.valueOf(value);
                        } else if ("duplicated_lines_density".equals(metric)) {
                            duplications = Double.valueOf(value);
                        }
                    } catch (NumberFormatException ignored) {
                        // Leave the measure empty instead of inventing a number.
                    }
                }
            }
            return new Measures(coverage, bugs, vulnerabilities, codeSmells, duplications);
        } catch (Exception ex) {
            return Measures.empty();
        }
    }

    private HttpResponse<String> send(String url) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET();
        String token = properties.getToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String analyzedAt(String base, String key) {
        try {
            HttpResponse<String> response = send(base + "/api/components/show?component=" + key);
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode date = mapper.readTree(response.body()).path("component").path("analysisDate");
            if (date.isMissingNode() || date.isNull() || date.asText().isBlank()) {
                return null;
            }
            return date.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private record Measures(Double coverage, Integer bugs, Integer vulnerabilities, Integer codeSmells, Double duplications) {
        static Measures empty() {
            return new Measures(null, null, null, null, null);
        }
    }
}
