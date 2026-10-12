package com.dcp.pipeline.sonar;

import com.dcp.pipeline.api.QualityView;
import com.dcp.pipeline.config.SonarProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarProbeTest {

    private static final String MEASURES = "{\"component\":{\"measures\":["
            + "{\"metric\":\"coverage\",\"value\":\"24.7\"},"
            + "{\"metric\":\"bugs\",\"value\":\"3\"},"
            + "{\"metric\":\"vulnerabilities\",\"value\":\"10\"},"
            + "{\"metric\":\"code_smells\",\"value\":\"71\"},"
            + "{\"metric\":\"duplicated_lines_density\",\"value\":\"2.8\"},"
            + "{\"metric\":\"bugs\",\"value\":\"not-a-number\"}"
            + "]}}";

    @Test
    void measuresAreReadFromSonarAndTheTokenIsSentAsABearerHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.compareAndSet(null, exchange.getRequestHeaders().getFirst("Authorization"));
            String path = exchange.getRequestURI().getPath();
            String body;
            if ("/api/qualitygates/project_status".equals(path)) {
                body = "{\"projectStatus\":{\"status\":\"ERROR\"}}";
            } else if ("/api/measures/component".equals(path)) {
                body = MEASURES;
            } else if ("/api/components/show".equals(path)) {
                body = "{\"component\":{\"analysisDate\":\"2026-10-11T20:54:52+0000\"}}";
            } else {
                body = "";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body.isEmpty() ? 404 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            SonarProperties properties = properties("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setToken("sonar-test-token");
            QualityView view = new SonarProbe(properties).read();

            assertTrue(view.available());
            assertEquals("ERROR", view.qualityGate());
            assertEquals(24.7, view.coverage());
            assertEquals(3, view.bugs());
            assertEquals(10, view.vulnerabilities());
            assertEquals(71, view.codeSmells());
            assertEquals(2.8, view.duplications());
            assertEquals("2026-10-11T20:54:52+0000", view.analyzedAt());
            assertTrue(view.url().contains("dashboard?id=devops-control-plane"));
            assertEquals("Bearer sonar-test-token", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aMissingProjectDoesNotInventMeasures() {
        QualityView view = new SonarProbe(properties("http://127.0.0.1:9")).read();

        assertFalse(view.available());
        assertNull(view.qualityGate());
        assertNull(view.bugs());
        assertTrue(view.message().contains("unreachable"));
    }

    @Test
    void anEmptyUrlDoesNotCallSonar() {
        QualityView view = new SonarProbe(properties("")).read();

        assertFalse(view.available());
        assertTrue(view.message().contains("empty"));
    }

    private static SonarProperties properties(String url) {
        SonarProperties properties = new SonarProperties();
        properties.setMode("off");
        properties.setUrl(url);
        properties.setProjectKey("devops-control-plane");
        properties.setPublicUrl("http://127.0.0.1:9000");
        return properties;
    }
}
