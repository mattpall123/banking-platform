package com.bank.backend.auth.service;

import com.bank.backend.user.domain.UserAccount;
import com.bank.backend.user.repository.UserAccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Verifies a Bearer JWT on every request, sets the SecurityContext on success.
 *
 * Critically: failures here do NOT throw — we just leave the SecurityContext
 * empty and let the request continue. Spring Security's authorization layer
 * (down the chain) returns 401 if the endpoint required auth.
 *
 * This separation matters: a malformed token shouldn't blow up the global
 * exception handler with a 500. Auth failure is just "no auth", which becomes
 * 401 only when an endpoint demands it.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountRepository userRepo;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.verify(token);
            Long userId = Long.parseLong(claims.getSubject());

            // Load user fresh from DB on every request — accepts the cost in
            // exchange for being able to revoke a user (disable, lock) instantly.
            // For higher scale we'd cache this for ~30s with Caffeine.
            UserAccount user = userRepo.findById(userId).orElse(null);
            if (user == null || !user.isEnabled()) {
                chain.doFilter(request, response);
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            if (roles == null) roles = List.of();

            CurrentUser principal = new CurrentUser(userId, user.getEmail(), roles);

            var authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();

            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException | IllegalArgumentException e) {
            // Bad signature, expired, malformed — log at DEBUG (not WARN; legitimate
            // expired tokens are very common) and let the request continue unauthenticated.
            log.debug("JWT validation failed: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}