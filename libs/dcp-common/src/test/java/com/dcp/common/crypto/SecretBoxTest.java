package com.dcp.common.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SecretBoxTest {

    @Test
    void roundTripHidesPlaintext() {
        SecretBox box = new SecretBox("dev-only-env-key-change-me");
        String encrypted = box.encrypt("super-secret");
        assertNotEquals("super-secret", encrypted);
        assertEquals("super-secret", box.decrypt(encrypted));
    }
}
