package com.dcp.application.api;

public record InternalEnvVarView(String key, String value, boolean secret) {
}
