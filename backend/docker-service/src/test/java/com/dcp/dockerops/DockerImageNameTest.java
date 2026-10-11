package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerImageNameTest {

    @Test
    void blankRegistryKeepsTheLocalDcpName() {
        assertEquals("dcp/demo", ImageNames.repository("demo"));
        assertEquals("dcp/demo", ImageNames.imageName("", "dcp/demo"));
        assertEquals("dcp/demo", ImageNames.imageName(null, "dcp/demo"));
        assertEquals("dcp/demo:1", ImageNames.fullImage("", "dcp/demo", "1"));
        assertEquals("dcp/demo:1", ImageNames.fullImage(null, "dcp/demo", "1"));
    }

    @Test
    void publicRegistryPrefixesTheNameReturnedToCallers() {
        assertEquals("localhost:15000/dcp/demo", ImageNames.imageName("localhost:15000", ImageNames.repository("demo")));
        assertEquals("localhost:15000/dcp/demo:1", ImageNames.fullImage("localhost:15000/", "dcp/demo", "1"));
        assertEquals("registry:5000/dcp/demo:9", ImageNames.fullImage("registry:5000", "dcp/demo", "9"));
    }

    @Test
    void generatesATagAndRejectsNamesOutsideDcp() {
        assertTrue(ResourcePolicies.IMAGE_TAG.matcher(ImageNames.tag(" ")).matches());
        assertEquals("release-1", ImageNames.tag(" release-1 "));
        assertThrows(ApiException.class, () -> ImageNames.repository("Ubuntu"));
        assertThrows(ApiException.class, () -> ImageNames.imageName("docker.io", "ubuntu"));
        assertThrows(ApiException.class, () -> ImageNames.fullImage("https://evil.example", "dcp/demo", "1"));
        assertThrows(ApiException.class, () -> ImageNames.requireOwned("ubuntu:latest"));
    }
}
