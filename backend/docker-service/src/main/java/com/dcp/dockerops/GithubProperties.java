package com.dcp.dockerops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dcp.github")
public class GithubProperties {

    private String token = "";
    private String allowedOrgs = "";
    private boolean enforceAllowlist;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAllowedOrgs() {
        return allowedOrgs;
    }

    public void setAllowedOrgs(String allowedOrgs) {
        this.allowedOrgs = allowedOrgs;
    }

    public boolean isEnforceAllowlist() {
        return enforceAllowlist;
    }

    public void setEnforceAllowlist(boolean enforceAllowlist) {
        this.enforceAllowlist = enforceAllowlist;
    }
}
