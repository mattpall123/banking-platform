package com.bank.backend.customer.dto;

import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.domain.IdType;
import com.bank.backend.customer.domain.KycStatus;
import com.bank.backend.customer.domain.RiskRating;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Customer view for the /me endpoint.
 *
 * Notice what's NOT here: id_number_last4 is present but full PII like the
 * full ID is never stored, so impossible to leak. We DO expose risk_rating
 * and kyc_status because the customer has a right to know their KYC state.
 */
public record CustomerResponse(
    Long id,
    String legalFirstName,
    String legalLastName,
    LocalDate dateOfBirth,
    String email,
    String phone,
    String streetAddress,
    String city,
    String province,
    String postalCode,
    String country,
    String occupation,
    IdType idType,
    String idNumberLast4,
    LocalDate idExpiryDate,
    boolean isPep,
    KycStatus kycStatus,
    Instant kycVerifiedAt,
    RiskRating riskRating,
    Instant createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
            c.getId(),
            c.getLegalFirstName(),
            c.getLegalLastName(),
            c.getDateOfBirth(),
            c.getEmail(),
            c.getPhone(),
            c.getStreetAddress(),
            c.getCity(),
            c.getProvince(),
            c.getPostalCode(),
            c.getCountry(),
            c.getOccupation(),
            c.getIdType(),
            c.getIdNumberLast4(),
            c.getIdExpiryDate(),
            c.isPep(),
            c.getKycStatus(),
            c.getKycVerifiedAt(),
            c.getRiskRating(),
            c.getCreatedAt()
        );
    }
}