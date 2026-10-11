package com.dcp.deployment.client;

public record RemoteEnvVar(String key, String value, boolean secret) {
}
