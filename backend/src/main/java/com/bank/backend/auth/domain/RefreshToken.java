package com.bank.backend.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A refresh token. Stored as a SHA-256 hash of the actual token string.
 *
 * Active tokens have revoked_at = null. Rotation creates a new row and sets
 * the old row's revoked_at + replaced_by, which gives us an audit trail.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(name = "created_from_ip", length = 45)
    private String createdFromIp;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}