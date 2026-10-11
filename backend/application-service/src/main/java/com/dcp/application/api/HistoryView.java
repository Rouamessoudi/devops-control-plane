package com.dcp.application.api;

import java.time.Instant;
import java.util.UUID;

public record HistoryView(
        UUID id,
        UUID applicationId,
        UUID actorId,
        String action,
        String summary,
        Instant createdAt
) {
}
