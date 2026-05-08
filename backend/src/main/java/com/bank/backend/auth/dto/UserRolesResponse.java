package com.bank.backend.auth.dto;

import java.util.List;

public record UserRolesResponse(
    Long userId,
    String email,
    List<String> roles
) {}