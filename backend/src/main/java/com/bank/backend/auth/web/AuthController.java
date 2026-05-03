package com.bank.backend.auth.web;

import com.bank.backend.auth.dto.AuthResponse;
import com.bank.backend.auth.dto.LoginRequest;
import com.bank.backend.auth.dto.RefreshRequest;
import com.bank.backend.auth.dto.RegisterRequest;
import com.bank.backend.auth.service.AuthService;
import com.bank.backend.auth.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest body,
            HttpServletRequest request
    ) {
        AuthResponse resp = authService.register(body, clientIp(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(authService.login(body, clientIp(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(authService.refresh(body.refreshToken(), clientIp(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CurrentUser user) {
        authService.logout(user.userId());
        return ResponseEntity.noContent().build();
    }

    /** Best-effort client IP extraction. X-Forwarded-For is set by load balancers. */
    private String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}