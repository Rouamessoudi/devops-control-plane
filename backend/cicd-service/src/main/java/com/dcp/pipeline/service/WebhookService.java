package com.dcp.pipeline.service;

import com.dcp.common.security.JwtService;
import com.dcp.common.web.InternalRestClient;
import com.dcp.pipeline.api.PipelineDtos.NotificationCommand;
import com.dcp.pipeline.api.PipelineDtos.PipelineView;
import com.dcp.pipeline.config.GithubProperties;
import com.dcp.pipeline.domain.WebhookEventEntity;
import com.dcp.pipeline.domain.WebhookEventRepository;
import com.dcp.pipeline.github.GithubSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public enum Outcome {
        SECRET_MISSING,
        BAD_SIGNATURE,
        ACCEPTED
    }

    private final GithubProperties githubProperties;
    private final WebhookEventRepository events;
    private final PipelineStore store;
    private final ObjectMapper objectMapper;
    private final InternalRestClient notificationClient;

    public WebhookService(GithubProperties githubProperties,
                          WebhookEventRepository events,
                          PipelineStore store,
                          ObjectMapper objectMapper,
                          @Qualifier("notificationClient") InternalRestClient notificationClient) {
        this.githubProperties = githubProperties;
        this.events = events;
        this.store = store;
        this.objectMapper = objectMapper;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public Outcome receive(byte[] body, String signature, String delivery, String event) {
        String secret = githubProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return Outcome.SECRET_MISSING;
        }
        byte[] payload = body == null ? new byte[0] : body;
        boolean verified = GithubSignature.verify(secret, payload, signature);
        String sha = JwtService.sha256Hex(new String(payload, StandardCharsets.UTF_8));
        String deliveryId = deliveryId(delivery, sha);
        String eventType = eventType(event);
        WebhookEventEntity row = events.findByDeliveryId(deliveryId).orElse(null);
        boolean alreadyVerified = row != null && row.isVerified();
        if (row == null) {
            row = new WebhookEventEntity();
            row.setId(UUID.randomUUID());
            row.setDeliveryId(deliveryId);
            row.setReceivedAt(Instant.now());
            row.setVerified(verified);
            row.setEventType(eventType);
            row.setPayloadSha256(sha);
            events.save(row);
        } else if (verified && !row.isVerified()) {
            row.setVerified(true);
            row.setEventType(eventType);
            row.setPayloadSha256(sha);
        }
        if (!verified) {
            return Outcome.BAD_SIGNATURE;
        }
        if (alreadyVerified) {
            return Outcome.ACCEPTED;
        }
        PipelineView updated = apply(payload, deliveryId, eventType);
        if (updated != null) {
            notifyAfterCommit(updated);
        }
        return Outcome.ACCEPTED;
    }

    private PipelineView apply(byte[] payload, String deliveryId, String eventType) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        UUID pipelineId = pipelineId(root);
        if (pipelineId == null) {
            return null;
        }
        return store.applyWebhook(pipelineId, deliveryId, eventType, conclusion(root));
    }

    private void notifyAfterCommit(PipelineView pipeline) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(pipeline);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(pipeline);
            }
        });
    }

    private void deliver(PipelineView pipeline) {
        try {
            notificationClient.post("/internal/notifications", new NotificationCommand(
                    pipeline.requestedBy(),
                    "Pipeline " + pipeline.status(),
                    "Pipeline " + pipeline.id() + " finished with " + pipeline.conclusion()));
        } catch (Exception ex) {
            log.warn("Notification was not delivered for pipeline {}: {}", pipeline.id(), ex.getMessage());
        }
    }

    private static UUID pipelineId(JsonNode root) {
        UUID direct = uuid(root.get("pipeline_id"));
        if (direct != null) {
            return direct;
        }
        JsonNode inputs = root.get("inputs");
        if (inputs != null && inputs.isObject()) {
            return uuid(inputs.get("pipeline_id"));
        }
        return null;
    }

    private static UUID uuid(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String conclusion(JsonNode root) {
        String status = text(root, "status");
        String conclusion = text(root, "conclusion");
        JsonNode run = root.get("workflow_run");
        if (run != null && run.isObject()) {
            if (status == null) {
                status = text(run, "status");
            }
            if (conclusion == null) {
                conclusion = text(run, "conclusion");
            }
        }
        if ("completed".equalsIgnoreCase(status) && "success".equalsIgnoreCase(conclusion)) {
            return PipelineStore.SUCCESS;
        }
        if ("failure".equalsIgnoreCase(conclusion) || "failure".equalsIgnoreCase(status)) {
            return PipelineStore.FAILURE;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String deliveryId(String delivery, String sha) {
        if (delivery == null || delivery.isBlank() || delivery.length() > 80) {
            return sha;
        }
        return delivery.trim();
    }

    private static String eventType(String event) {
        if (event == null || event.isBlank()) {
            return "unknown";
        }
        String trimmed = event.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }
}
