package com.bank.backend.auth.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,        // always "Bearer"
    long expiresIn           // access token TTL in seconds
) {}