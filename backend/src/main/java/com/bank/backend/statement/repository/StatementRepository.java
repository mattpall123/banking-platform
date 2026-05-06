package com.bank.backend.statement.repository;

import com.bank.backend.statement.domain.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByAccountIdInOrderByPeriodYearDescPeriodMonthDesc(List<Long> accountIds);

    Optional<Statement> findByAccountIdAndPeriodYearAndPeriodMonth(
            Long accountId, Integer year, Integer month);
}