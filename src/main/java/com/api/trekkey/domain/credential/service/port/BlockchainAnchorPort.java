package com.api.trekkey.domain.credential.service.port;

import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Signature65;
import com.api.trekkey.domain.credential.entity.ChainOperationType;

public interface BlockchainAnchorPort {

    OnChainIssuerKey getIssuerKey(Hash32 issuerId, long keyVersion);

    OnChainBatch getBatch(Hash32 batchIdHash);

    OnChainCredentialStatus getCredentialStatus(Hash32 issuerId, Hash32 credentialIdHash);

    PreparedTransaction prepareBatch(Eip712.BatchApproval approval, Signature65 issuerSignature);

    PreparedTransaction prepareStatus(Eip712.StatusApproval approval, Signature65 issuerSignature);

    void broadcast(PreparedTransaction transaction);

    ChainReceipt getReceipt(Hash32 transactionHash, ChainOperationType operationType);

    record OnChainIssuerKey(
            EthereumAddress signer,
            long validFrom,
            long validUntil,
            long compromisedAt,
            boolean exists) {
    }

    record OnChainBatch(
            Hash32 issuerId,
            Hash32 merkleRoot,
            Hash32 schemaVersionHash,
            long leafCount,
            int treeVersion,
            long issuerKeyVersion,
            long anchoredAt,
            boolean exists) {
    }

    record OnChainCredentialStatus(
            CredentialChainState state,
            long effectiveAt,
            long recordedAt,
            long issuerKeyVersion,
            Hash32 replacementCredentialIdHash) {
    }

    record PreparedTransaction(
            Hash32 transactionHash,
            long transactionNonce,
            EthereumAddress relayerAddress,
            byte[] signedRawTransaction) {

        public PreparedTransaction {
            if (transactionHash == null || transactionNonce < 0 || relayerAddress == null || signedRawTransaction == null
                    || signedRawTransaction.length == 0) {
                throw new IllegalArgumentException("prepared transaction fields are required");
            }
            signedRawTransaction = signedRawTransaction.clone();
        }

        @Override
        public byte[] signedRawTransaction() {
            return signedRawTransaction.clone();
        }
    }

    record ChainReceipt(
            ReceiptState state,
            long blockNumber,
            Hash32 blockHash,
            int eventLogIndex,
            String revertReason) {

        public static ChainReceipt pending() {
            return new ChainReceipt(ReceiptState.PENDING, 0, null, -1, null);
        }
    }

    enum CredentialChainState {
        NONE,
        REVOKED,
        SUPERSEDED
    }

    enum ReceiptState {
        PENDING,
        CONFIRMED,
        REVERTED
    }
}
