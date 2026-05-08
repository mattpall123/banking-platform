package com.bank.backend.auth.service;

import com.bank.backend.auth.repository.UserRoleRepository;
import com.bank.backend.user.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies HS256-signed JWTs.
 *
 * Access tokens are short-lived (default 15 min). Refresh tokens are NOT
 * JWTs — they're opaque random strings stored hashed in refresh_tokens.
 * That's the modern best practice: JWTs for stateless access, opaque tokens
 * for revocable refresh.
 */
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey signingKey;
    private final UserRoleRepository userRoleRepo;

    public JwtService(JwtProperties props, UserRoleRepository userRoleRepo) {
        this.props = props;
        this.userRoleRepo = userRoleRepo;
        // HS256 requires at least 256 bits (32 bytes) of key material.
        // Keys.hmacShaKeyFor enforces this and throws if the secret is too short.
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issue an access token for the given user. Roles are loaded fresh
     * from the DB at mint time, so a role grant/revoke takes effect on
     * the next token (refresh or new login).
     */
    public String issueAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.accessTokenTtlSeconds());

        List<String> roles = userRoleRepo.findRoleCodesByUserId(user.getId());

        return Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .id(UUID.randomUUID().toString())          // jti
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and verify a token. Throws JwtException on bad signature,
     * expired token, or any other validation failure. Caller is expected
     * to catch and return 401.
     */
    public Claims verify(String token) throws JwtException {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public long getAccessTokenTtlSeconds() {
        return props.accessTokenTtlSeconds();
    }

    public long getRefreshTokenTtlSeconds() {
        return props.refreshTokenTtlSeconds();
    }
}