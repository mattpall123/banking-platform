package com.bank.backend.account.repository;

import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.domain.Account;
import com.bank.backend.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bank.backend.customer.domain.Customer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, Long> {
    List<AccountHolder> findByAccount(Account account);
    List<AccountHolder> findByCustomer(Customer customer);


    @Query("""
        SELECT DISTINCT ah FROM AccountHolder ah
          JOIN FETCH ah.account
         WHERE ah.customer = :customer
           AND ah.removedAt IS NULL
    """)
    List<AccountHolder> findActiveByCustomerWithAccount(@Param("customer") Customer customer);
}

