package com.dcp.application.api;

public record EnvVarRequest(String key, String value, Boolean secret) {
}
