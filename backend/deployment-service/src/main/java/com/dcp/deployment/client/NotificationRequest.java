package com.dcp.deployment.client;

import java.util.UUID;

public record NotificationRequest(UUID userId, String title, String message) {
}
