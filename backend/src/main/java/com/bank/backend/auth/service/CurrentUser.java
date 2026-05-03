package com.bank.backend.auth.service;

import java.util.List;

/**
 * The "principal" attached to a Spring Security Authentication after the
 * JWT filter validates a token. Lightweight — just what we extract from
 * the token claims. Controllers can inject this via @AuthenticationPrincipal.
 */
public record CurrentUser(
    Long userId,
    String email,
    List<String> roles
) {}