package com.bank.backend.etransfer.repository;

import com.bank.backend.etransfer.domain.ETransferAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ETransferAttemptRepository extends JpaRepository<ETransferAttempt, Long> {

    long countByEtransferIdAndCorrectFalse(Long etransferId);

    List<ETransferAttempt> findByEtransferIdOrderByAttemptedAtDesc(Long etransferId);
}