package com.bank.backend.etransfer.dto;

import com.bank.backend.etransfer.domain.AutoDepositSetting;

public record AutoDepositResponse(
    Long id,
    String email,
    Long targetAccountId,
    Boolean enabled
) {
    public static AutoDepositResponse from(AutoDepositSetting a) {
        return new AutoDepositResponse(a.getId(), a.getEmail(), a.getTargetAccountId(), a.getEnabled());
    }
}