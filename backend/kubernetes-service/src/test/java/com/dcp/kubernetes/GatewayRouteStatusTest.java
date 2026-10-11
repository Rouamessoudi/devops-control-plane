package com.dcp.kubernetes;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteStatus;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.RouteParentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRouteStatusTest {

    @Test
    void acceptedRequiresBothAcceptedAndResolvedRefs() {
        assertTrue(GatewayRouteStatus.accepted(route("Accepted", "True", "ResolvedRefs", "True")));
    }

    @Test
    void storedRouteWithoutGatewayAcceptanceIsNotTraffic() {
        assertFalse(GatewayRouteStatus.accepted(new HTTPRoute()));
        assertFalse(GatewayRouteStatus.accepted(route("Accepted", "False", "ResolvedRefs", "False")));
        assertFalse(GatewayRouteStatus.accepted(route("Accepted", "True", "ResolvedRefs", "False")));
    }

    private static HTTPRoute route(String firstType, String firstStatus, String secondType, String secondStatus) {
        Condition first = new Condition();
        first.setType(firstType);
        first.setStatus(firstStatus);
        Condition second = new Condition();
        second.setType(secondType);
        second.setStatus(secondStatus);
        RouteParentStatus parent = new RouteParentStatus();
        parent.setConditions(List.of(first, second));
        HTTPRouteStatus status = new HTTPRouteStatus();
        status.setParents(List.of(parent));
        HTTPRoute route = new HTTPRoute();
        route.setStatus(status);
        return route;
    }
}
