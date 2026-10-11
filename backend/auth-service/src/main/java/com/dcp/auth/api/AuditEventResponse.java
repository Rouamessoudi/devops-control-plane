package com.dcp.auth.api;

import com.dcp.auth.domain.AuthAudit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, UUID actorId, String action, String ipAddress, String details, Instant createdAt) {

    public static AuditEventResponse from(AuthAudit event) {
        return new AuditEventResponse(
                event.getId(),
                event.getActorId(),
                event.getAction(),
                event.getIpAddress(),
                event.getDetails(),
                event.getCreatedAt());
    }
}
