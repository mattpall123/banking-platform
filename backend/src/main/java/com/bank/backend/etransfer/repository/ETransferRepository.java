package com.bank.backend.etransfer.repository;

import com.bank.backend.etransfer.domain.ETransfer;
import com.bank.backend.etransfer.domain.ETransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ETransferRepository extends JpaRepository<ETransfer, Long> {

    /** Outgoing for a user (newest first). */
    List<ETransfer> findBySenderUserIdOrderByCreatedAtDesc(Long userId);

    /** Incoming pending transfers to a given email (case-insensitive). */
    @Query("""
        SELECT e FROM ETransfer e
         WHERE LOWER(e.recipientEmail) = LOWER(:email)
           AND e.status = :status
         ORDER BY e.createdAt DESC
    """)
    List<ETransfer> findByRecipientEmailAndStatus(
            @Param("email") String email,
            @Param("status") ETransferStatus status
    );

    /**
     * Pessimistic-write lookup for claim/cancel — locks the row so two
     * concurrent claims can't both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ETransfer e WHERE e.id = :id")
    Optional<ETransfer> findByIdForUpdate(@Param("id") Long id);

    /** Expiry job's hot query: PENDING transfers past their expiry. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT e FROM ETransfer e
         WHERE e.status = :status
           AND e.expiresAt <= :now
    """)
    List<ETransfer> findDueForExpiry(
            @Param("status") ETransferStatus status,
            @Param("now") Instant now
    );
}