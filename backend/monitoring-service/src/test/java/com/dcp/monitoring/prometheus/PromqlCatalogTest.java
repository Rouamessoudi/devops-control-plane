package com.dcp.monitoring.prometheus;

import com.dcp.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromqlCatalogTest {

    private final PromqlCatalog catalog = new PromqlCatalog("dcp");

    @Test
    void cpuAndMemoryUseTheValidatedNamespace() {
        assertEquals("sum(process_cpu_usage{job=\"devops-control-plane\"})", catalog.cpu());
        assertEquals("sum(jvm_memory_used_bytes{job=\"devops-control-plane\"})", catalog.memory());
        assertEquals("count(kube_pod_info{namespace=\"dcp\"})", catalog.pods());
        assertEquals(catalog.cpu(), catalog.series("cpu"));
        assertEquals(catalog.memory(), catalog.series("memory"));
        assertEquals(catalog.httpRate(), catalog.series("http"));
        assertEquals(catalog.httpErrors(), catalog.series("errors"));
        assertEquals(catalog.httpLatency(), catalog.series("latency"));
        assertEquals(catalog.httpClientErrors(), catalog.series("clientErrors"));
        assertEquals(catalog.podCpu(), catalog.series("podCpu"));
        assertEquals(catalog.podMemory(), catalog.series("podMemory"));
        assertEquals(catalog.restarts(), catalog.series("restarts"));
        assertEquals(catalog.availableDeployments(), catalog.series("deployments"));
        assertEquals("count(up{job=\"devops-control-plane\"} == 1)", catalog.availability());
    }

    @Test
    void unknownMetricIsRejected() {
        ApiException exception = assertThrows(ApiException.class, () -> catalog.series("disk"));
        assertEquals(400, exception.status());
        assertThrows(ApiException.class, () -> catalog.series(null));
    }

    @Test
    void illegalRangeIsRejected() {
        ApiException exception = assertThrows(ApiException.class, () -> PromqlCatalog.range("2h"));
        assertEquals(400, exception.status());
        assertThrows(ApiException.class, () -> PromqlCatalog.stepSeconds("7d"));
        assertEquals(60, PromqlCatalog.stepSeconds("15m"));
        assertEquals(60, PromqlCatalog.stepSeconds("1h"));
        assertEquals(300, PromqlCatalog.stepSeconds("6h"));
        assertEquals(300, PromqlCatalog.stepSeconds("24h"));
    }

    @Test
    void illegalNamespaceIsRejected() {
        assertThrows(ApiException.class, () -> new PromqlCatalog("Not_A_Label"));
    }
}
