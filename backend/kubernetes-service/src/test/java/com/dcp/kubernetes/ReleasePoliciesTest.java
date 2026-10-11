package com.dcp.kubernetes;

import com.dcp.common.error.ApiException;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleasePoliciesTest {

    @Test
    void blueGreenTrackRejectsAnythingElse() {
        assertEquals("green", ReleasePolicies.otherTrack("blue"));
        assertEquals("blue", ReleasePolicies.otherTrack("green"));
        assertThrows(ApiException.class, () -> ReleasePolicies.track("stable"));
        assertThrows(ApiException.class, () -> ReleasePolicies.rollbackCause(0, 0, 0, false, false, 3));
    }

    @Test
    void acceptsOnlyConfiguredCanaryWeights() {
        assertEquals(10, ReleasePolicies.canaryWeight(10));
        assertEquals(25, ReleasePolicies.canaryWeight(25));
        assertEquals(50, ReleasePolicies.canaryWeight(50));
        assertEquals(100, ReleasePolicies.canaryWeight(100));
        assertThrows(ApiException.class, () -> ReleasePolicies.canaryWeight(15));
        assertThrows(ApiException.class, () -> ReleasePolicies.canaryWeight(0));
    }

    @Test
    void rollbackCauseNamesTheObservedFailure() {
        assertNull(ReleasePolicies.rollbackCause(2, 2, 0, false, false, 3));
        assertEquals("CrashLoopBackOff detected", ReleasePolicies.rollbackCause(1, 0, 1, true, true, 3));
        assertEquals("restart count 4 exceeds limit 3", ReleasePolicies.rollbackCause(1, 1, 4, false, false, 3));
        assertEquals("readiness probe did not reach 2 ready replicas; ready=0", ReleasePolicies.rollbackCause(2, 0, 0, false, true, 3));
    }

    @Test
    void autoscalerRejectsLimitsOutsideThePlatformCap() {
        assertThrows(ApiException.class, () -> ReleasePolicies.autoscaler(1, 6, 70, 80, 5));
        assertThrows(ApiException.class, () -> ReleasePolicies.autoscaler(3, 2, 70, 80, 5));
        assertThrows(ApiException.class, () -> ReleasePolicies.autoscaler(1, 3, 0, 80, 5));
    }

    @Test
    void canaryRouteUsesGatewayApiWeights() {
        HTTPRoute route = ReleaseManifests.canaryRoute("dcp", "demo", "dcp", "demo-stable", "demo-canary", 25);
        assertEquals("HTTPRoute", route.getKind());
        assertEquals("gateway.networking.k8s.io/v1", route.getApiVersion());
        assertEquals("dcp", route.getSpec().getParentRefs().get(0).getName());
        assertEquals(75, route.getSpec().getRules().get(0).getBackendRefs().get(0).getWeight());
        assertEquals("demo-stable", route.getSpec().getRules().get(0).getBackendRefs().get(0).getName());
        assertEquals(25, route.getSpec().getRules().get(0).getBackendRefs().get(1).getWeight());
        assertEquals("demo-canary", route.getSpec().getRules().get(0).getBackendRefs().get(1).getName());
    }

    @Test
    void blueServiceSelectsOnlyTheReadyTrack() {
        UUID applicationId = UUID.randomUUID();
        Deployment deployment = ReleaseManifests.trackedDeployment("dcp", "demo-green", "green", "registry:5000/dcp/demo:2", 1, 8080, applicationId);
        var service = ReleaseManifests.trackService("dcp", "demo", "demo-green", "green", 8080, applicationId);
        assertEquals("green", deployment.getSpec().getSelector().getMatchLabels().get("dcp.track"));
        assertEquals("demo", service.getSpec().getSelector().get("app"));
        assertEquals("green", service.getSpec().getSelector().get("dcp.track"));
        assertEquals(1, deployment.getSpec().getReplicas());
    }

    @Test
    void autoscalerTargetsTheDeployment() {
        var autoscaler = ReleaseManifests.autoscaler("dcp", "demo", "demo", 1, 5, 70, 80, UUID.randomUUID());
        assertEquals("HorizontalPodAutoscaler", autoscaler.getKind());
        assertEquals(1, autoscaler.getSpec().getMinReplicas());
        assertEquals(5, autoscaler.getSpec().getMaxReplicas());
        assertEquals("demo", autoscaler.getSpec().getScaleTargetRef().getName());
        assertEquals("cpu", autoscaler.getSpec().getMetrics().get(0).getResource().getName());
        assertEquals(70, autoscaler.getSpec().getMetrics().get(0).getResource().getTarget().getAverageUtilization());
        assertEquals("memory", autoscaler.getSpec().getMetrics().get(1).getResource().getName());
    }
}
