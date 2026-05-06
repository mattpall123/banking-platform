package com.bank.backend.statement.repository;

import com.bank.backend.statement.domain.StatementRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRunRepository extends JpaRepository<StatementRun, Long> {

    List<StatementRun> findTop10ByOrderByStartedAtDesc();
}