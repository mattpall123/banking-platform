package com.bank.backend.auth.service;

import com.bank.backend.auth.domain.Role;
import com.bank.backend.auth.domain.RoleCode;
import com.bank.backend.auth.domain.UserRole;
import com.bank.backend.auth.dto.AuthResponse;
import com.bank.backend.auth.dto.LoginRequest;
import com.bank.backend.auth.dto.RegisterRequest;
import com.bank.backend.auth.repository.RoleRepository;
import com.bank.backend.auth.repository.UserRoleRepository;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.domain.KycStatus;
import com.bank.backend.customer.domain.RiskRating;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.shared.MetricsService;
import com.bank.backend.user.domain.UserAccount;
import com.bank.backend.user.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Orchestrates registration, login, refresh, and logout.
 *
 * Registration is a single transaction creating the User (auth), the
 * Customer (KYC pending), and the user's default CUSTOMER role grant.
 * Atomicity matters: a failure in any step rolls back the whole thing.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private final UserAccountRepository userRepo;
    private final CustomerRepository customerRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final MetricsService metrics;

    public AuthService(
            UserAccountRepository userRepo,
            CustomerRepository customerRepo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            MetricsService metrics,
            UserRoleRepository userRoleRepo,
            RoleRepository roleRepo
    ) {
        this.userRepo = userRepo;
        this.customerRepo = customerRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.metrics = metrics;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req, String fromIp) {
        String email = req.email().toLowerCase().trim();

        if (userRepo.existsByEmail(email)) {
            // Don't reveal whether the email exists. Indistinguishable responses
            // defeat user enumeration.
            throw new IllegalArgumentException("Registration failed");
        }

        // 1. Create the User
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEnabled(true);
        userRepo.save(user);

        // 2. Create the Customer (FINTRAC fields, KYC pending)
        Customer customer = new Customer();
        customer.setUserId(user.getId());
        customer.setLegalFirstName(req.legalFirstName());
        customer.setLegalLastName(req.legalLastName());
        customer.setDateOfBirth(req.dateOfBirth());
        customer.setEmail(email);
        customer.setPhone(req.phone());
        customer.setStreetAddress(req.streetAddress());
        customer.setCity(req.city());
        customer.setProvince(req.province().toUpperCase());
        customer.setPostalCode(req.postalCode());
        customer.setCountry("CA");
        customer.setOccupation(req.occupation());
        customer.setIdType(req.idType());
        customer.setIdNumberLast4(req.idNumberLast4());
        customer.setIdExpiryDate(req.idExpiryDate());
        customer.setPep(req.pep());
        customer.setKycStatus(KycStatus.PENDING);
        customer.setRiskRating(RiskRating.LOW);
        customerRepo.save(customer);

        // 3. Grant the default CUSTOMER role
        Role customerRole = roleRepo.findByCode(RoleCode.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing from DB"));
        UserRole grant = new UserRole();
        grant.setUserId(user.getId());
        grant.setRoleId(customerRole.getId());
        grant.setGrantedAt(Instant.now());
        userRoleRepo.save(grant);

        log.info("Registered new user id={} email={}", user.getId(), email);

        return issueTokensFor(user, fromIp);
    }

    @Transactional(noRollbackFor = {
        org.springframework.security.authentication.BadCredentialsException.class,
        org.springframework.security.authentication.LockedException.class
    })
    public AuthResponse login(LoginRequest req, String fromIp) {
        String email = req.email().toLowerCase().trim();

        UserAccount user = userRepo.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new LockedException("Account is temporarily locked");
        }

        if (!user.isEnabled()) {
            throw new LockedException("Account is disabled");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int newCount = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(newCount);

            if (newCount >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_MINUTES * 60));
                log.warn("Account locked after {} failed attempts: userId={}", newCount, user.getId());
            }

            userRepo.save(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        log.info("User logged in: userId={}", user.getId());
        metrics.failedLogin();
        return issueTokensFor(user, fromIp);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken, String fromIp) {
        try {
            var rotation = refreshTokenService.rotate(refreshToken, fromIp);
            UserAccount user = userRepo.findById(rotation.userId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown user"));

            String newAccess = jwtService.issueAccessToken(user);
            return new AuthResponse(
                    newAccess,
                    rotation.newRefreshToken(),
                    "Bearer",
                    jwtService.getAccessTokenTtlSeconds()
            );
        } catch (RefreshTokenReusedException e) {
            log.warn("Refresh token reuse detected for userId={} — revoking all tokens", e.getUserId());
            refreshTokenService.revokeAllForUser(e.getUserId());
            throw new BadCredentialsException("Refresh token invalid; please log in again");
        }
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
        log.info("User logged out: userId={}", userId);
    }

    // ---- helpers ----

    private AuthResponse issueTokensFor(UserAccount user, String fromIp) {
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = refreshTokenService.issue(user, fromIp);
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        );
    }
}