package com.bank.backend.auth.dto;

import com.bank.backend.customer.domain.IdType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Registration payload. Matches the FINTRAC-aligned Customer fields from V2.
 *
 * Validation runs at the controller boundary via @Valid. We never pass raw
 * input to the domain layer.
 */
public record RegisterRequest(
    // ---- Auth ----
    @NotBlank @Email @Size(max = 255)
    String email,

    @NotBlank
    @Size(min = 12, max = 100, message = "Password must be 12-100 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
        message = "Password must contain lowercase, uppercase, digit, and special character"
    )
    String password,

    // ---- Identity ----
    @NotBlank @Size(max = 100) String legalFirstName,
    @NotBlank @Size(max = 100) String legalLastName,
    @NotNull @Past LocalDate dateOfBirth,
    @NotBlank @Size(max = 20) String phone,

    // ---- Address ----
    @NotBlank @Size(max = 200) String streetAddress,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(min = 2, max = 2) String province,
    @NotBlank @Size(max = 10) String postalCode,

    // ---- FINTRAC ----
    @NotBlank @Size(max = 100) String occupation,
    @NotNull IdType idType,
    @NotBlank @Size(min = 4, max = 4) String idNumberLast4,
    @NotNull @Future LocalDate idExpiryDate,
    boolean pep
) {}