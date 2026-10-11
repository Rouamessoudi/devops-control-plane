package com.dcp.kubernetes;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImagePolicyTest {

    private static final String REGISTRIES = "localhost:15000,registry:5000";

    @Test
    void acceptsPlatformImages() {
        assertDoesNotThrow(() -> ImagePolicy.require("dcp/demo:1", REGISTRIES));
        assertDoesNotThrow(() -> ImagePolicy.require("localhost:15000/dcp/demo:1", REGISTRIES));
        assertDoesNotThrow(() -> ImagePolicy.require("registry:5000/dcp/demo:9", REGISTRIES));
    }

    @Test
    void rejectsForeignImages() {
        assertThrows(ApiException.class, () -> ImagePolicy.require("ubuntu:latest", REGISTRIES));
        assertThrows(ApiException.class, () -> ImagePolicy.require("../evil:1", REGISTRIES));
        assertThrows(ApiException.class, () -> ImagePolicy.require("docker.io/library/ubuntu:latest", REGISTRIES));
        assertThrows(ApiException.class, () -> ImagePolicy.require("mysql:8", REGISTRIES));
        assertThrows(ApiException.class, () -> ImagePolicy.require("dcp/demo@sha256:abc", REGISTRIES));
        assertThrows(ApiException.class, () -> ImagePolicy.require("evil.example/dcp/demo:1", REGISTRIES));
    }

    @Test
    void dnsLabelRejectsUppercase() {
        ApiException ex = assertThrows(ApiException.class, () -> ResourcePolicies.dnsLabel("MyApp", "name"));
        assertEquals(400, ex.status());
    }
}
