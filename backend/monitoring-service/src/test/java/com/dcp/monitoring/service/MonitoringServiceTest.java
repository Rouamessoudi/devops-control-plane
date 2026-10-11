package com.dcp.monitoring.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.web.InternalRestClient;
import com.dcp.monitoring.config.MonitoringProperties.GrafanaProperties;
import com.dcp.monitoring.domain.MonitorTargetRepository;
import com.dcp.monitoring.prometheus.PromqlCatalog;
import com.dcp.monitoring.prometheus.PrometheusClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock
    private MonitorTargetRepository targets;

    @Mock
    private PrometheusClient prometheus;

    @Mock
    private InternalRestClient kubernetes;

    @Test
    void actuatorSeriesAreReturnedWhenPodRestartsAreAbsent() {
        when(prometheus.instant(anyString())).thenAnswer(invocation -> sample(invocation.getArgument(0)));
        var overview = service().overview();

        assertEquals(0.4, overview.cpuCores());
        assertEquals(2048.0, overview.memoryBytes());
        assertEquals(8.0, overview.podCount());
        assertNull(overview.restartCount());
        assertEquals("Kubernetes pod metrics are absent. CPU and memory come from Prometheus actuator series.", overview.message());
    }

    @Test
    void emptyPrometheusStaysEmpty() {
        when(prometheus.instant(anyString())).thenReturn(null);

        var overview = service().overview();

        assertNull(overview.cpuCores());
        assertEquals(PromqlCatalog.NO_SAMPLES, overview.message());
    }

    @Test
    void logTailOutsideTheAllowedRangeIsRejected() {
        ApiException exception = assertThrows(ApiException.class, () -> service().logs("api-gateway", 0));
        assertEquals(400, exception.status());
        org.mockito.Mockito.verifyNoInteractions(kubernetes);
    }

    @Test
    void logsUseTheKubernetesApi() {
        when(kubernetes.get("/internal/kubernetes/pods/api-gateway/logs?tail=20", String.class)).thenReturn("line");

        assertEquals("line", service().logs("api-gateway", 20));
    }

    private MonitoringService service() {
        return new MonitoringService(targets, new PromqlCatalog("dcp"), prometheus, new GrafanaProperties(), kubernetes);
    }

    private static Double sample(String query) {
        if (query.contains("restarts_total")) {
            return null;
        }
        if (query.contains("process_cpu_usage")) {
            return 0.4;
        }
        if (query.contains("area=\"heap\"")) {
            return 512.0;
        }
        if (query.contains("jvm_memory_used_bytes")) {
            return 2048.0;
        }
        if (query.contains("kube_pod_info") || query.contains("up{job")) {
            return 8.0;
        }
        return null;
    }
}
