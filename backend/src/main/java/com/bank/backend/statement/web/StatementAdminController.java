package com.bank.backend.statement.web;

import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.statement.domain.StatementRun;
import com.bank.backend.statement.dto.StatementRunResponse;
import com.bank.backend.statement.repository.StatementRunRepository;
import com.bank.backend.statement.service.StatementJobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.bank.backend.statement.service.DemoDataSeeder;
import java.time.YearMonth;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
/**
 * Admin endpoints for the statement-generation batch job.
 *
 * AUTHZ NOTE: should be admin-only. Until Session 9 introduces RBAC, every
 * authenticated user can trigger runs. Acceptable for development.
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/statements")
public class StatementAdminController {

    private final StatementJobLauncher launcher;
    private final StatementRunRepository runRepo;
    private final DemoDataSeeder seeder;

    public StatementAdminController(StatementJobLauncher launcher,
                                    StatementRunRepository runRepo,
                                    DemoDataSeeder seeder) {
        this.launcher = launcher;
        this.runRepo = runRepo;
        this.seeder = seeder;
    }

    /**
     * Trigger a statement-generation run for the given period (e.g. "2026-04").
     * Synchronous: blocks until the job finishes.
     */
    @PostMapping("/run")
    public ResponseEntity<StatementRunResponse> run(
            @RequestParam String yearMonth,
            @AuthenticationPrincipal CurrentUser user
    ) {
        YearMonth period = YearMonth.parse(yearMonth);   // throws DateTimeParseException → 400 via global handler
        StatementRun run = launcher.launch(period, user.userId());
        return ResponseEntity.ok(StatementRunResponse.from(run));
    }

    /**
     * Most recent batch runs, newest first. Default limit 10.
     */
    @GetMapping("/runs")
    public ResponseEntity<List<StatementRunResponse>> runs() {
        List<StatementRunResponse> body = runRepo.findTop10ByOrderByStartedAtDesc().stream()
                .map(StatementRunResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Seed N synthetic transactions on the given account. Useful for
     * filling out a month's worth of varied transactions before
     * generating a statement.
     */
    @PostMapping("/seed")
    public ResponseEntity<java.util.Map<String, Object>> seed(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "30") int count,
            @AuthenticationPrincipal CurrentUser user
    ) {
        int posted = seeder.seedRealistic(accountId, count, user.userId());
        return ResponseEntity.ok(java.util.Map.of(
                "accountId", accountId,
                "requested", count,
                "posted", posted
        ));
    }
}