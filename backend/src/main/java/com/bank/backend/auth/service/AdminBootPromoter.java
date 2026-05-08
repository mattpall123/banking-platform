package com.bank.backend.auth.service;

import com.bank.backend.auth.domain.Role;
import com.bank.backend.auth.domain.RoleCode;
import com.bank.backend.auth.domain.UserRole;
import com.bank.backend.auth.repository.RoleRepository;
import com.bank.backend.auth.repository.UserRoleRepository;
import com.bank.backend.user.domain.UserAccount;
import com.bank.backend.user.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Dev convenience: at boot, promote the configured email to ADMIN.
 *
 * Idempotent — running multiple times has no effect after the first.
 *
 * Disabled in production by leaving the property unset (or setting it to
 * an empty string). Real production admin grants come through the admin
 * endpoint, executed by another admin who has logged in.
 *
 * Why ApplicationRunner not @PostConstruct: Spring's lifecycle has fully
 * completed by ApplicationRunner, including transactions; @PostConstruct
 * runs before the transaction manager is fully ready.
 */
@Component
public class AdminBootPromoter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootPromoter.class);

    private final UserAccountRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final String email;

    public AdminBootPromoter(
            UserAccountRepository userRepo,
            RoleRepository roleRepo,
            UserRoleRepository userRoleRepo,
            @Value("${bank.admin.boot-promote-email:}") String email
    ) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            log.debug("No bank.admin.boot-promote-email configured; skipping admin bootstrap");
            return;
        }

        Optional<UserAccount> userOpt = userRepo.findByEmail(email.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            log.info("Boot admin promotion: user {} not found, skipping", email);
            return;
        }
        UserAccount user = userOpt.get();

        Role adminRole = roleRepo.findByCode(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing"));

        // Already admin? Skip.
        boolean alreadyAdmin = userRoleRepo.findByUserId(user.getId()).stream()
                .anyMatch(ur -> ur.getRoleId().equals(adminRole.getId()));
        if (alreadyAdmin) {
            log.debug("Boot admin promotion: {} already has ADMIN role", email);
            return;
        }

        UserRole grant = new UserRole();
        grant.setUserId(user.getId());
        grant.setRoleId(adminRole.getId());
        grant.setGrantedAt(Instant.now());
        // grantedByUserId stays null — system-granted, not user-granted
        userRoleRepo.save(grant);

        log.info("Boot admin promotion: granted ADMIN to {}", email);
    }
}