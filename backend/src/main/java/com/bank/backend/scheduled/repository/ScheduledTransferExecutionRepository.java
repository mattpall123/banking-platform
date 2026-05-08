package com.bank.backend.scheduled.repository;

import com.bank.backend.scheduled.domain.ScheduledTransferExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduledTransferExecutionRepository extends JpaRepository<ScheduledTransferExecution, Long> {

    Optional<ScheduledTransferExecution> findByIdempotencyKey(String idempotencyKey);

    List<ScheduledTransferExecution> findByScheduledTransferIdOrderByPlannedAtDesc(Long scheduledTransferId);
}