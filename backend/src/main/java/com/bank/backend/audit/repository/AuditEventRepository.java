package com.bank.backend.audit.repository;

import com.bank.backend.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * The most recent audit event by id. Used during write to chain to the
     * previous row's hash.
     *
     * IMPORTANT: this is called within a transaction that's about to insert
     * a new row. We rely on Postgres' default REPEATABLE READ semantics
     * within the audit insert transaction (or use SELECT ... FOR UPDATE
     * if you needed stronger guarantees on ordering under heavy concurrency).
     * For our scale, ordering by id DESC is sufficient.
     */
    @Query("SELECT a FROM AuditEvent a ORDER BY a.id DESC LIMIT 1")
    Optional<AuditEvent> findMostRecent();

    Page<AuditEvent> findAllByOrderByIdDesc(Pageable pageable);
}