package com.dcp.deployment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClusterImagesTest {

    @Test
    void rewritesTheHostRegistryToTheNameTheClusterCanPull() {
        assertEquals("registry:5000/dcp/vote:1",
                ClusterImages.forCluster("localhost:15000/dcp/vote:1", "localhost:15000", "registry:5000"));
    }

    @Test
    void leavesAnImageUnchangedWhenRegistriesMatchOrAreAbsent() {
        assertEquals("registry:5000/dcp/vote:1",
                ClusterImages.forCluster("registry:5000/dcp/vote:1", "localhost:15000", "registry:5000"));
        assertEquals("registry:5000/dcp/vote:1",
                ClusterImages.forCluster("registry:5000/dcp/vote:1", "registry:5000", "registry:5000"));
        assertEquals("dcp/vote:1", ClusterImages.forCluster("dcp/vote:1", "", "registry:5000"));
    }
}
