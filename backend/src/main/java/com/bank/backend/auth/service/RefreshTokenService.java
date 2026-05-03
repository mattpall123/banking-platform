package com.bank.backend.auth.service;

import com.bank.backend.auth.domain.RefreshToken;
import com.bank.backend.auth.repository.RefreshTokenRepository;
import com.bank.backend.user.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, validates, and rotates opaque refresh tokens.
 *
 * The token returned to clients is a 256-bit URL-safe random string. We
 * store SHA-256(token) in the DB, never the token itself. Same threat
 * model as passwords: if the DB leaks, attackers can't replay tokens.
 *
 * Rotation: every refresh issues a new token AND revokes the old one.
 * Reusing a revoked token is treated as suspected theft — we revoke ALL
 * the user's tokens (forced re-login).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final JwtService jwtService;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo, JwtService jwtService) {
        this.repo = repo;
        this.jwtService = jwtService;
    }

    /**
     * Issue a new refresh token for the user. Returns the raw token string
     * (only time it exists in plaintext); we store only the hash.
     */
    @Transactional
    public String issue(UserAccount user, String fromIp) {
        String raw = generateRawToken();
        String hash = sha256Hex(raw);

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash);
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(jwtService.getRefreshTokenTtlSeconds()));
        token.setCreatedFromIp(fromIp);
        repo.save(token);

        return raw;
    }

    /**
     * Validate a presented token and rotate it. Returns the user the token
     * belongs to, after revoking the old token and issuing a new one.
     *
     * Throws if the token is unknown, revoked, or expired. If a *revoked*
     * token is presented, that's a strong signal of theft — caller (AuthService)
     * should revoke all tokens for that user.
     */
    @Transactional
    public RotationResult rotate(String rawToken, String fromIp) {
        String hash = sha256Hex(rawToken);
        RefreshToken existing = repo.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Unknown refresh token"));

        if (existing.getRevokedAt() != null) {
            // Token reuse — possible theft. Caller should revoke all tokens.
            throw new RefreshTokenReusedException(existing.getUserId());
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }

        // Rotate: revoke the old, issue the new
        existing.revoke();
        String newRaw = generateRawToken();
        String newHash = sha256Hex(newRaw);

        RefreshToken next = new RefreshToken();
        next.setUserId(existing.getUserId());
        next.setTokenHash(newHash);
        next.setIssuedAt(Instant.now());
        next.setExpiresAt(Instant.now().plusSeconds(jwtService.getRefreshTokenTtlSeconds()));
        next.setCreatedFromIp(fromIp);
        repo.save(next);

        existing.setReplacedBy(next.getId());

        return new RotationResult(existing.getUserId(), newRaw);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repo.revokeAllForUser(userId, Instant.now());
    }

    // ---- helpers ----

    private String generateRawToken() {
        byte[] bytes = new byte[32];               // 256 bits of entropy
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record RotationResult(Long userId, String newRefreshToken) {}
}