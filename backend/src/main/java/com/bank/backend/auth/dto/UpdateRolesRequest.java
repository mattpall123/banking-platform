package com.bank.backend.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for replacing a user's role set.
 *
 * Semantics: the supplied list IS the new role set (full replacement).
 * Partial updates (add one role, keep others) would need a different
 * endpoint shape. We keep this simple.
 */
public record UpdateRolesRequest(
    @NotNull @NotEmpty List<String> roles
) {}