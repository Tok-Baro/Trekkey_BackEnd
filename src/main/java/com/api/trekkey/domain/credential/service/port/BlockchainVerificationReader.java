package com.api.trekkey.domain.credential.service.port;

import com.api.trekkey.domain.credential.crypto.Hash32;

/** Verification cannot prepare, sign, submit or reconcile transactions. */
public interface BlockchainVerificationReader {
    BlockchainAnchorPort.OnChainIssuerKey getIssuerKey(Hash32 issuerId, long keyVersion);
    BlockchainAnchorPort.OnChainBatch getBatch(Hash32 batchIdHash);
    BlockchainAnchorPort.OnChainCredentialStatus getCredentialStatus(Hash32 issuerId, Hash32 credentialIdHash);
}
