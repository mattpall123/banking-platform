package com.bank.backend.etransfer.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "etransfer_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ETransferAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "etransfer_id", nullable = false)
    private Long etransferId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();

    @Column(name = "attempted_by_user_id")
    private Long attemptedByUserId;

    @Column(nullable = false)
    private Boolean correct;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}