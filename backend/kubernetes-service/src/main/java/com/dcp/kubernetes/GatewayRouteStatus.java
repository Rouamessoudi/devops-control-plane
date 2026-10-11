package com.dcp.kubernetes;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.RouteParentStatus;

final class GatewayRouteStatus {

    private GatewayRouteStatus() {
    }

    static boolean accepted(HTTPRoute route) {
        if (route == null || route.getStatus() == null || route.getStatus().getParents() == null) {
            return false;
        }
        boolean accepted = false;
        boolean resolved = false;
        for (RouteParentStatus parent : route.getStatus().getParents()) {
            if (parent.getConditions() == null) {
                continue;
            }
            for (Condition condition : parent.getConditions()) {
                if (!"True".equals(condition.getStatus())) {
                    continue;
                }
                if ("Accepted".equals(condition.getType())) {
                    accepted = true;
                }
                if ("ResolvedRefs".equals(condition.getType())) {
                    resolved = true;
                }
            }
        }
        return accepted && resolved;
    }
}
