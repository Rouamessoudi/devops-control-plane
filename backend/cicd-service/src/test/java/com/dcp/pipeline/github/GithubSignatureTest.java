package com.dcp.pipeline.github;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubSignatureTest {

    @Test
    void acceptsKnownBodyAndRejectsFlippedBit() throws Exception {
        String secret = "webhook-secret";
        byte[] body = "known-body".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + hmac(secret, body);
        assertTrue(GithubSignature.verify(secret, body, header));
        byte[] flipped = body.clone();
        flipped[0] = (byte) (flipped[0] ^ 0x01);
        assertFalse(GithubSignature.verify(secret, flipped, header));
    }

    @Test
    void blankSecretDoesNotVerify() throws Exception {
        byte[] body = "known-body".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + hmac("webhook-secret", body);
        assertFalse(GithubSignature.verify("", body, header));
        assertFalse(GithubSignature.verify("   ", body, header));
        assertFalse(GithubSignature.verify(null, body, header));
    }

    private static String hmac(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
