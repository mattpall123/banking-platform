package com.bank.backend.scheduled.repository;

import com.bank.backend.scheduled.domain.ScheduledTransfer;
import com.bank.backend.scheduled.domain.ScheduledTransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, Long> {

    List<ScheduledTransfer> findBySourceAccountIdInOrderByNextRunAtAsc(List<Long> sourceAccountIds);

    /**
     * The runner's main query. Pessimistic-write lock so two runner
     * instances can't fight for the same schedule.
     *
     * Pessimistic locking on a periodically-scanned table is fine here
     * because the runner is single-threaded and the schedule volume is
     * tiny. If it grew we'd shard or add a job_lease column.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s FROM ScheduledTransfer s
         WHERE s.status = :status
           AND s.nextRunAt <= :now
         ORDER BY s.nextRunAt ASC
    """)
    List<ScheduledTransfer> findDue(
            @Param("status") ScheduledTransferStatus status,
            @Param("now") Instant now
    );
}