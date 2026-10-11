package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GithubAllowlistTest {

    @Test
    void enforceAllowlistWithEmptyOrgsRejects() {
        assertThrows(ApiException.class, () -> GithubAllowlist.check("https://github.com/acme/demo", true, ""));
        assertThrows(ApiException.class, () -> GithubAllowlist.check("https://github.com/acme/demo", true, "   "));
        assertThrows(ApiException.class, () -> GithubAllowlist.check("https://github.com/acme/demo", true, null));
    }

    @Test
    void nonBlankAllowlistMustContainTheOrganization() {
        GithubAllowlist.check("https://github.com/acme/demo.git", true, "acme");
        assertThrows(ApiException.class, () -> GithubAllowlist.check("https://github.com/acme/demo", false, "other"));
    }
}
