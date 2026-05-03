package com.bank.backend.account.domain;

public enum HolderRole {
    PRIMARY_OWNER,    // the principal owner; on a sole account, the only owner
    JOINT_OWNER,      // joint account, same rights as primary
    SIGNER            // can transact but has no ownership rights (e.g., POA)
}