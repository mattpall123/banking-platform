package com.bank.backend.auth.web;

import com.bank.backend.auth.domain.Role;
import com.bank.backend.auth.domain.RoleCode;
import com.bank.backend.auth.domain.UserRole;
import com.bank.backend.auth.dto.UpdateRolesRequest;
import com.bank.backend.auth.dto.UserRolesResponse;
import com.bank.backend.auth.repository.RoleRepository;
import com.bank.backend.auth.repository.UserRoleRepository;
import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.user.domain.UserAccount;
import com.bank.backend.user.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Admin endpoints for managing role grants.
 *
 *   GET  /api/admin/users/{id}/roles    - list a user's roles
 *   PUT  /api/admin/users/{id}/roles    - replace a user's role set
 *
 * ADMIN-only, enforced by class-level @PreAuthorize.
 *
 * Replacement semantics (PUT, not PATCH): the body is the new role set.
 * To revoke ADMIN, send {"roles": ["CUSTOMER"]} — straightforward, hard
 * to mis-specify.
 *
 * SAFETY RAILS we DON'T enforce here but real banks do:
 *   - prevent the last ADMIN from demoting themselves (lockout risk)
 *   - require dual control for ADMIN grants (one admin proposes, another approves)
 *   - require recent re-authentication for sensitive role changes
 * Mentioned in interviews as "future-work / production hardening."
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRolesController {

    private static final Set<String> VALID_ROLES = Set.of(
            RoleCode.CUSTOMER, RoleCode.TELLER, RoleCode.ADMIN
    );

    private final UserAccountRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;

    public AdminRolesController(
            UserAccountRepository userRepo,
            RoleRepository roleRepo,
            UserRoleRepository userRoleRepo
    ) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
    }

    @GetMapping("/{userId}/roles")
    public ResponseEntity<UserRolesResponse> getRoles(@PathVariable Long userId) {
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<String> roles = userRoleRepo.findRoleCodesByUserId(userId);
        return ResponseEntity.ok(new UserRolesResponse(user.getId(), user.getEmail(), roles));
    }

    @PutMapping("/{userId}/roles")
    @Transactional
    public ResponseEntity<UserRolesResponse> setRoles(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateRolesRequest req,
            @AuthenticationPrincipal CurrentUser admin
    ) {
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validate every requested role exists in our enum
        for (String code : req.roles()) {
            if (!VALID_ROLES.contains(code)) {
                throw new IllegalArgumentException("Unknown role: " + code);
            }
        }

        // Remove existing grants
        List<UserRole> existing = userRoleRepo.findByUserId(userId);
        userRoleRepo.deleteAll(existing);
        userRoleRepo.flush();   // ensure deletes hit DB before inserts

        // Insert new grants
        for (String code : req.roles()) {
            Role role = roleRepo.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Role not found: " + code));
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            ur.setGrantedAt(Instant.now());
            ur.setGrantedByUserId(admin.userId());
            userRoleRepo.save(ur);
        }

        List<String> newRoles = userRoleRepo.findRoleCodesByUserId(userId);
        return ResponseEntity.ok(new UserRolesResponse(user.getId(), user.getEmail(), newRoles));
    }
}