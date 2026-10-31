package com.phenom.selfservicebooking.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds,
        UserResponse user) {
}
