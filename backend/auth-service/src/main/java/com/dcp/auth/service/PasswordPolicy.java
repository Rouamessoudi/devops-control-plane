package com.dcp.auth.service;

import com.dcp.common.error.ApiException;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void requireStrong(String password) {
        if (password == null
                || password.length() < 10
                || password.length() > 72
                || !hasUpper(password)
                || !hasLower(password)
                || !hasDigit(password)) {
            throw ApiException.badRequest(
                    "Password must be 10 to 72 characters and include an uppercase letter, a lowercase letter, and a digit");
        }
    }

    private static boolean hasUpper(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (Character.isUpperCase(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLower(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (Character.isLowerCase(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDigit(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (Character.isDigit(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
