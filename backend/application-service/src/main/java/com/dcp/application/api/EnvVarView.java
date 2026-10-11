package com.dcp.application.api;

import java.util.UUID;

public record EnvVarView(UUID id, String key, boolean secret, String value) {
}
