package com.dcp.auth.api;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn, String tokenType, UserResponse user) {
}
