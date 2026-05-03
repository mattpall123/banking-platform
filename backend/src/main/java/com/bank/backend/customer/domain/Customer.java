package com.bank.backend.customer.domain;

import com.bank.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The bank's KYC'd customer record. Distinct from User (auth) and Account (product).
 *
 * A Customer may or may not have an associated User (e.g., walk-in customers
 * onboarded by a teller will have Customer first, User later when they enroll
 * in online banking).
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA needs no-args; protected keeps it out of normal use
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;  // optional FK to users.id

    // ---- Identity ----
    @Column(name = "legal_first_name", nullable = false, length = 100)
    private String legalFirstName;

    @Column(name = "legal_last_name", nullable = false, length = 100)
    private String legalLastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    // ---- Address ----
    @Column(name = "street_address", nullable = false, length = 200)
    private String streetAddress;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 2)
    private String province;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(nullable = false, length = 2)
    private String country = "CA";

    // ---- FINTRAC required ----
    @Column(nullable = false, length = 100)
    private String occupation;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 30)
    private IdType idType;

    @Column(name = "id_number_last4", nullable = false, length = 4)
    private String idNumberLast4;

    @Column(name = "id_expiry_date", nullable = false)
    private LocalDate idExpiryDate;

    @Column(name = "is_pep", nullable = false)
    private boolean isPep = false;

    // ---- KYC workflow ----
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_verified_at")
    private Instant kycVerifiedAt;

    @Column(name = "kyc_verified_by")
    private Long kycVerifiedBy;

    // ---- Risk ----
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", nullable = false, length = 10)
    private RiskRating riskRating = RiskRating.LOW;
}