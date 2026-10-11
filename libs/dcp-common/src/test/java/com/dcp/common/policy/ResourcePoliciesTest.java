package com.dcp.common.policy;

import com.dcp.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourcePoliciesTest {

    @Test
    void acceptsPublicGithubHttpsRepository() {
        assertEquals("https://github.com/acme/demo", ResourcePolicies.githubRepository("https://github.com/acme/demo/"));
        assertEquals("acme", ResourcePolicies.organization("https://github.com/acme/demo.git"));
    }

    @Test
    void rejectsNonGithubAndPathEscape() {
        assertThrows(ApiException.class, () -> ResourcePolicies.githubRepository("https://gitlab.com/acme/demo"));
        assertThrows(ApiException.class, () -> ResourcePolicies.branch("../main"));
        assertThrows(ApiException.class, () -> ResourcePolicies.relativePath("../Dockerfile", "Dockerfile"));
        assertThrows(ApiException.class, () -> ResourcePolicies.appName("Bad_Name"));
    }
}
