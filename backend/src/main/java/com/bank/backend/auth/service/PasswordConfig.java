package com.bank.backend.auth.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * BCrypt with work factor 12 — ~250ms per hash on modern hardware.
     * Slow enough to deter brute force, fast enough that login feels instant.
     *
     * Work factor is recorded in the hash itself ($2a$12$...), so we can
     * upgrade to factor 14 in the future without invalidating old hashes —
     * Spring Security will detect the old factor on login and rehash transparently.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}