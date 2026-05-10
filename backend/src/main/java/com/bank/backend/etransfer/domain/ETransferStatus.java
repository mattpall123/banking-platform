package com.bank.backend.etransfer.domain;

public enum ETransferStatus {
    PENDING,    // sent, in holding account, awaiting claim
    COMPLETED,  // recipient claimed; funds in their account
    CANCELLED,  // sender cancelled before claim
    EXPIRED,    // recipient didn't claim within window
    LOCKED      // 3 wrong security answers; treated like EXPIRED for refund
}