package com.api.trekkey.domain.credential.service.support;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialStatusEvent;
import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import java.math.BigInteger;

public final class ApprovalPayloadFactory {

    private ApprovalPayloadFactory() {
    }

    public static Eip712.Domain domain(BlockchainProperties properties) {
        return new Eip712.Domain(
                BigInteger.valueOf(properties.getChainId()),
                EthereumAddress.fromHex(properties.getContractAddress()));
    }

    public static Eip712.BatchApproval batch(
            String issuerPublicId,
            AncBatch batch,
            AncIssuerKey issuerKey) {
        return new Eip712.BatchApproval(
                Hashing.issuerId(issuerPublicId),
                Hash32.of(batch.getBatchIdHash()),
                Hash32.of(batch.getMerkleRoot()),
                Hash32.of(batch.getSchemaVersionHash()),
                BigInteger.valueOf(batch.getLeafCount()),
                BigInteger.valueOf(batch.getTreeVersion()),
                BigInteger.valueOf(issuerKey.getKeyVersion()),
                BigInteger.valueOf(batch.getApprovalNonce()),
                BigInteger.valueOf(UtcTime.toInstant(batch.getApprovalDeadline()).getEpochSecond()));
    }

    public static Eip712.StatusApproval status(
            String issuerPublicId,
            AncCredentialStatusEvent event,
            AncCredential credential,
            AncIssuerKey issuerKey,
            AncCredential replacement) {
        Eip712.StatusAction action = event.getNextStatus() == CredentialStatus.REVOKED
                ? Eip712.StatusAction.REVOKE
                : Eip712.StatusAction.SUPERSEDE;
        return new Eip712.StatusApproval(
                Hashing.issuerId(issuerPublicId),
                Hash32.of(credential.getCredentialIdHash()),
                action,
                replacement == null ? Hash32.ZERO : Hash32.of(replacement.getCredentialIdHash()),
                BigInteger.valueOf(UtcTime.toInstant(event.getEffectiveAt()).getEpochSecond()),
                BigInteger.valueOf(issuerKey.getKeyVersion()),
                BigInteger.valueOf(event.getApprovalNonce()),
                BigInteger.valueOf(UtcTime.toInstant(event.getApprovalDeadline()).getEpochSecond()));
    }
}
