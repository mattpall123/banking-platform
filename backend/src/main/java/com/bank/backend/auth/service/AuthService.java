package com.bank.backend.auth.service;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.domain.AccountStatus;
import com.bank.backend.account.domain.AccountType;
import com.bank.backend.account.domain.HolderRole;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.account.repository.AccountRepository;
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
import com.bank.backend.ledger.domain.JournalEntryType;
import com.bank.backend.ledger.domain.LedgerAccount;
import com.bank.backend.ledger.domain.LedgerAccountType;
import com.bank.backend.ledger.repository.LedgerAccountRepository;
import com.bank.backend.ledger.service.LedgerService;
import com.bank.backend.ledger.service.PostingRequest;
import com.bank.backend.shared.MetricsService;
import com.bank.backend.shared.Money;
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
import java.util.List;

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
    private final AccountRepository accountRepo;
    private final AccountHolderRepository holderRepo;
    private final LedgerAccountRepository ledgerAccountRepo;
    private final LedgerService ledger;

    public AuthService(
            UserAccountRepository userRepo,
            CustomerRepository customerRepo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            MetricsService metrics,
            UserRoleRepository userRoleRepo,
            RoleRepository roleRepo,
            AccountRepository accountRepo,
            AccountHolderRepository holderRepo,
            LedgerAccountRepository ledgerAccountRepo,
            LedgerService ledger
    ) {
        this.userRepo = userRepo;
        this.customerRepo = customerRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.metrics = metrics;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.accountRepo = accountRepo;
        this.holderRepo = holderRepo;
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.ledger = ledger;
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

        // 4. Auto-create starter accounts with a small demo balance
        openStarterAccount(user, customer, AccountType.CHEQUING, Money.of("1500.00", "CAD"));
        openStarterAccount(user, customer, AccountType.SAVINGS,  Money.of("500.00",  "CAD"));

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

    private void openStarterAccount(UserAccount user, Customer customer, AccountType type, Money opening) {
        // 1. Banking account row
        Account acct = new Account();
        acct.setAccountNumber(generateAccountNumber());
        acct.setAccountType(type);
        acct.setCurrency("CAD");
        acct.setStatus(AccountStatus.ACTIVE);
        acct.setOpenedAt(Instant.now());
        acct = accountRepo.save(acct);

        // 2. Link customer as primary owner
        AccountHolder holder = new AccountHolder();
        holder.setAccount(acct);
        holder.setCustomer(customer);
        holder.setRole(HolderRole.PRIMARY_OWNER);
        holderRepo.save(holder);

        // 3. Ledger sub-account (LIABILITY) for balance tracking
        LedgerAccount sub = new LedgerAccount();
        sub.setCode("DEPOSITS:" + acct.getAccountNumber());
        sub.setName(customer.getLegalFirstName() + " " + type.name().charAt(0) + type.name().substring(1).toLowerCase());
        sub.setAccountType(LedgerAccountType.LIABILITY);
        sub.setCurrency("CAD");
        sub.setBankingAccountId(acct.getId());
        ledgerAccountRepo.save(sub);

        // 4. Seed opening balance via journal entry (debit EQUITY:OPENING, credit deposits)
        LedgerAccount equity = ledgerAccountRepo.findByCode("EQUITY:OPENING")
                .orElseThrow(() -> new IllegalStateException("EQUITY:OPENING ledger account missing"));
        ledger.post(
                "Opening balance",
                JournalEntryType.OPENING,
                "register-open-" + acct.getAccountNumber(),
                user.getId(),
                List.of(
                        PostingRequest.of(sub.getId(),    opening.negate()),
                        PostingRequest.of(equity.getId(), opening)
                )
        );

        log.info("Opened {} account {} for userId={} with {}", type, acct.getAccountNumber(), user.getId(), opening);
    }

    private static String generateAccountNumber() {
        return String.format("1%011d", System.currentTimeMillis() % 100_000_000_000L);
    }

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