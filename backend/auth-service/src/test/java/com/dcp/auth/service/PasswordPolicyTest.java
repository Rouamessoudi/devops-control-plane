package com.dcp.auth.service;

import com.dcp.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void rejectsWeakPasswords() {
        assertRejected(null);
        assertRejected("");
        assertRejected("Short1A");
        assertRejected("Password1");
        assertRejected("nouppercase1");
        assertRejected("ALLUPPERCASE1");
        assertRejected("NoDigitsHere");
        assertRejected("A1" + "a".repeat(71));
    }

    @Test
    void acceptsStrongPasswords() {
        assertDoesNotThrow(() -> PasswordPolicy.requireStrong("Abcdefghi1"));
        assertDoesNotThrow(() -> PasswordPolicy.requireStrong("GoodPassword1"));
        assertDoesNotThrow(() -> PasswordPolicy.requireStrong("A1" + "a".repeat(70)));
    }

    private static void assertRejected(String password) {
        ApiException exception = assertThrows(ApiException.class, () -> PasswordPolicy.requireStrong(password));
        assertEquals(400, exception.status());
    }
}
