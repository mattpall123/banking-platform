package com.bank.backend.customer.web;

import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.dto.CustomerResponse;
import com.bank.backend.customer.repository.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository customerRepo;

    public CustomerController(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    /**
     * Return the authenticated user's customer profile.
     *
     * IDOR-safe by design: there's no /api/customers/{id}. The user can only
     * ever see their own record because the lookup uses the authenticated
     * principal's userId, never a path/query param.
     */
    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> getMe(@AuthenticationPrincipal CurrentUser user) {
        Customer customer = customerRepo.findByUserId(user.userId())
                .orElseThrow(() -> new IllegalStateException(
                    "No customer record for authenticated user — should never happen"));

        return ResponseEntity.ok(CustomerResponse.from(customer));
    }
}