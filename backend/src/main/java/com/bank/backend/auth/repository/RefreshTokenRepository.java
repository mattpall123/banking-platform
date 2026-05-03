package com.bank.backend.auth.repository;

import com.bank.backend.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke every active refresh token for a user. Used on logout and on
     * suspected token theft (when a revoked token is presented for refresh).
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}