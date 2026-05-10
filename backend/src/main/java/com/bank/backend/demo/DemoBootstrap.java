package com.bank.backend.demo;

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
import com.bank.backend.auth.repository.RoleRepository;
import com.bank.backend.auth.repository.UserRoleRepository;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.domain.IdType;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.ledger.domain.JournalEntryType;
import com.bank.backend.ledger.domain.LedgerAccount;
import com.bank.backend.ledger.domain.LedgerAccountType;
import com.bank.backend.ledger.repository.LedgerAccountRepository;
import com.bank.backend.ledger.service.LedgerService;
import com.bank.backend.ledger.service.PostingRequest;
import com.bank.backend.shared.Money;
import com.bank.backend.user.domain.UserAccount;
import com.bank.backend.user.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Optional bootstrap that ensures a demo user exists in the live DB.
 *
 * Only runs when bank.demo.bootstrap.enabled=true. Idempotent:
 * if the user already exists, this is a no-op. Designed to be
 * turned ON for the first deploy, then turned OFF.
 *
 * Creates: a UserAccount + Customer + one CHEQUING account funded
 * with $5000 via an OPENING journal entry. ADMIN+CUSTOMER roles
 * attached so the demo user can show off everything including the
 * admin views.
 */
@Configuration
@ConditionalOnProperty(name = "bank.demo.bootstrap.enabled", havingValue = "true")
public class DemoBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DemoBootstrap.class);

    @Bean
    public ApplicationRunner demoBootstrapRunner(
            @Value("${bank.demo.bootstrap.email}") String email,
            @Value("${bank.demo.bootstrap.password}") String password,
            @Value("${bank.demo.bootstrap.first-name}") String firstName,
            @Value("${bank.demo.bootstrap.last-name}") String lastName,
            UserAccountRepository userRepo,
            CustomerRepository customerRepo,
            AccountRepository accountRepo,
            AccountHolderRepository holderRepo,
            LedgerAccountRepository ledgerAccountRepo,
            LedgerService ledger,
            RoleRepository roleRepo,
            UserRoleRepository userRoleRepo,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (password == null || password.isBlank()) {
                log.warn("DemoBootstrap: bank.demo.bootstrap.password is empty, skipping");
                return;
            }
            doBootstrap(email, password, firstName, lastName,
                    userRepo, customerRepo, accountRepo, holderRepo,
                    ledgerAccountRepo, ledger, roleRepo, userRoleRepo, passwordEncoder);
        };
    }

    @Transactional
    void doBootstrap(
            String email, String password, String firstName, String lastName,
            UserAccountRepository userRepo,
            CustomerRepository customerRepo,
            AccountRepository accountRepo,
            AccountHolderRepository holderRepo,
            LedgerAccountRepository ledgerAccountRepo,
            LedgerService ledger,
            RoleRepository roleRepo,
            UserRoleRepository userRoleRepo,
            PasswordEncoder passwordEncoder
    ) {
        if (userRepo.findByEmail(email).isPresent()) {
            log.info("DemoBootstrap: user {} already exists, skipping", email);
            return;
        }

        log.info("DemoBootstrap: creating demo user {}", email);

        // 1. UserAccount (createdAt/updatedAt auto-managed by JPA auditing)
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setFailedLoginCount(0);
        user = userRepo.save(user);

        // 2. Customer with KYC
        Customer customer = new Customer();
        customer.setUserId(user.getId());
        customer.setLegalFirstName(firstName);
        customer.setLegalLastName(lastName);
        customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        customer.setPhone("+14165551234");
        customer.setStreetAddress("1 Bay Street");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5J2N8");
        customer.setOccupation("Demo");
        customer.setIdType(IdType.DRIVERS_LICENCE);
        customer.setIdNumberLast4("0000");
        customer.setIdExpiryDate(LocalDate.of(2030, 12, 31));
        customer.setPep(false);
        customer = customerRepo.save(customer);

        // 3. Bank account (chequing, CAD)
        Account acct = new Account();
        acct.setAccountNumber(generateAccountNumber());
        acct.setAccountType(AccountType.CHEQUING);
        acct.setCurrency("CAD");
        acct.setStatus(AccountStatus.ACTIVE);
        acct.setOpenedAt(Instant.now());
        acct = accountRepo.save(acct);

        // 4. Account holder linkage
        AccountHolder holder = new AccountHolder();
        holder.setAccount(acct);
        holder.setCustomer(customer);
        holder.setRole(HolderRole.PRIMARY_OWNER);
        holderRepo.save(holder);

        // 5. Ledger sub-account for this banking account (LIABILITY)
        LedgerAccount sub = new LedgerAccount();
        sub.setCode("DEPOSITS:" + acct.getAccountNumber());
        sub.setName("Demo Chequing");
        sub.setAccountType(LedgerAccountType.LIABILITY);
        sub.setCurrency("CAD");
        sub.setBankingAccountId(acct.getId());
        ledgerAccountRepo.save(sub);

        // 6. Roles: ADMIN + CUSTOMER
        Role admin = roleRepo.findByCode(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing — V12 not applied?"));
        UserRole urAdmin = new UserRole();
        urAdmin.setUserId(user.getId());
        urAdmin.setRoleId(admin.getId());
        urAdmin.setGrantedAt(Instant.now());
        userRoleRepo.save(urAdmin);

        Role customerRole = roleRepo.findByCode(RoleCode.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing"));
        UserRole urCust = new UserRole();
        urCust.setUserId(user.getId());
        urCust.setRoleId(customerRole.getId());
        urCust.setGrantedAt(Instant.now());
        userRoleRepo.save(urCust);

        // 7. Fund the account with $5000 via OPENING journal entry.
        // Pattern: debit Equity (+$5000 to a -SUM EQUITY account, balance moves
        // toward zero from below), credit Deposits (-$5000 to a -SUM LIABILITY
        // account, balance grows). Net zero.
        LedgerAccount equity = ledgerAccountRepo.findByCode("EQUITY:OPENING")
                .orElseThrow(() -> new IllegalStateException("EQUITY:OPENING ledger account missing — V6 not applied?"));
        Money amount = Money.of("5000.00", "CAD");
        ledger.post(
                "Demo bootstrap funding",
                JournalEntryType.OPENING,
                "demo-bootstrap-" + acct.getAccountNumber(),
                user.getId(),
                List.of(
                        PostingRequest.of(sub.getId(),    amount.negate()),  // deposits +$5000
                        PostingRequest.of(equity.getId(), amount)            // equity drawn down
                )
        );

        log.info("DemoBootstrap: created user {} (id={}) with account {} funded $5000",
                email, user.getId(), acct.getAccountNumber());
    }

    /** Random-ish account number for demo. Real bank: dedicated generator with checksum. */
    private static String generateAccountNumber() {
        return "100000000" + String.format("%03d", (int)(Math.random() * 999));
    }
}