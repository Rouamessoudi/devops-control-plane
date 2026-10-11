package com.dcp.kubernetes;

import com.dcp.common.error.ApiException;

import java.util.Set;

public final class ReleasePolicies {

    public static final Set<Integer> CANARY_WEIGHTS = Set.of(10, 25, 50, 100);

    private ReleasePolicies() {
    }

    public static int canaryWeight(int weight) {
        if (!CANARY_WEIGHTS.contains(weight)) {
            throw ApiException.badRequest("Canary weight must be 10, 25, 50 or 100");
        }
        return weight;
    }

    public static String track(String value) {
        if (!"blue".equals(value) && !"green".equals(value)) {
            throw ApiException.badRequest("track must be blue or green");
        }
        return value;
    }

    public static String otherTrack(String track) {
        return "blue".equals(track(track)) ? "green" : "blue";
    }

    public static String rollbackCause(int desired, int ready, int restarts, boolean crashLoop, boolean readinessTimedOut, int maxRestarts) {
        if (desired < 1) {
            throw ApiException.badRequest("desired replicas must be at least 1");
        }
        if (crashLoop) {
            return "CrashLoopBackOff detected";
        }
        if (restarts > maxRestarts) {
            return "restart count " + restarts + " exceeds limit " + maxRestarts;
        }
        if (readinessTimedOut && ready < desired) {
            return "readiness probe did not reach " + desired + " ready replicas; ready=" + ready;
        }
        return null;
    }

    public static void autoscaler(int minReplicas, int maxReplicas, int cpuUtilization, int memoryUtilization, int platformMax) {
        if (minReplicas < 1) {
            throw ApiException.badRequest("minReplicas must be at least 1");
        }
        if (maxReplicas < minReplicas) {
            throw ApiException.badRequest("maxReplicas must be greater than or equal to minReplicas");
        }
        if (maxReplicas > platformMax) {
            throw ApiException.badRequest("maxReplicas exceeds the platform limit of " + platformMax);
        }
        if (cpuUtilization < 1 || cpuUtilization > 100 || memoryUtilization < 1 || memoryUtilization > 100) {
            throw ApiException.badRequest("CPU and memory thresholds must be between 1 and 100");
        }
    }
}
