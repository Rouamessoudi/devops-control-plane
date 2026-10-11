package com.dcp.pipeline.service;

import com.dcp.common.web.InternalRestClient;
import com.dcp.pipeline.config.GithubProperties;
import com.dcp.pipeline.domain.WebhookEventRepository;
import com.dcp.pipeline.github.GithubSignature;
import com.dcp.pipeline.service.WebhookService.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEventRepository events;

    @Mock
    private PipelineStore store;

    @Mock
    private InternalRestClient notifications;

    @Test
    void blankSecretStoresNothing() {
        WebhookService service = service("  ");

        Outcome outcome = service.receive("{}".getBytes(StandardCharsets.UTF_8), "sha256=abc", "delivery", "push");

        assertEquals(Outcome.SECRET_MISSING, outcome);
        verifyNoInteractions(events, store, notifications);
    }

    @Test
    void badSignatureIsStoredAsUnverifiedAndDoesNotStartAPipeline() {
        byte[] body = "{\"pipeline_id\":\"11111111-1111-1111-1111-111111111111\"}".getBytes(StandardCharsets.UTF_8);
        WebhookService service = service("webhook-secret-value-at-least-32b");
        when(events.findByDeliveryId(any())).thenReturn(java.util.Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Outcome outcome = service.receive(body, "sha256=deadbeef", "delivery-1", "workflow_job");

        assertEquals(Outcome.BAD_SIGNATURE, outcome);
        verify(events).save(any());
        verify(store, never()).applyWebhook(any(), any(), any(), any());
    }

    @Test
    void validSignatureIsAccepted() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String secret = "webhook-secret-value-at-least-32b";
        String header = signature(secret, body);
        WebhookService service = service(secret);
        when(events.findByDeliveryId(any())).thenReturn(java.util.Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Outcome outcome = service.receive(body, header, "delivery-2", "workflow_job");

        assertEquals(Outcome.ACCEPTED, outcome);
        verify(store, never()).applyWebhook(any(), any(), any(), any());
    }

    private WebhookService service(String secret) {
        GithubProperties properties = new GithubProperties();
        properties.setWebhookSecret(secret);
        return new WebhookService(properties, events, store, new ObjectMapper(), notifications);
    }

    private static String signature(String secret, byte[] body) {
        return GithubSignature.verify(secret, body, "sha256=00")
                ? "unused"
                : hmac(secret, body);
    }

    private static String hmac(String secret, byte[] body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
