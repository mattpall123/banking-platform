package com.bank.backend.account.repository;

import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.domain.Account;
import com.bank.backend.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, Long> {
    List<AccountHolder> findByAccount(Account account);
    List<AccountHolder> findByCustomer(Customer customer);
}