package com.api.trekkey.domain.credential.infrastructure.blockchain;

import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.crypto.Signature65;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;

/** Exact ABI boundary for TrekkeyCredentialRegistryV1. */
final class TrekkeyRegistryAbi {

    static final String GET_ISSUER_KEY = "getIssuerKey";
    static final String GET_BATCH = "getBatch";
    static final String GET_CREDENTIAL_STATUS = "getCredentialStatus";
    static final String ANCHOR_BATCH = "anchorBatch";
    static final String REVOKE_CREDENTIAL = "revokeCredential";
    static final String SUPERSEDE_CREDENTIAL = "supersedeCredential";

    static final Hash32 BATCH_ANCHORED_TOPIC = topic(
            "BatchAnchored(bytes32,bytes32,bytes32,bytes32,uint32,uint16,uint64,uint64)");
    static final Hash32 CREDENTIAL_REVOKED_TOPIC = topic(
            "CredentialRevoked(bytes32,bytes32,uint64,uint64)");
    static final Hash32 CREDENTIAL_SUPERSEDED_TOPIC = topic(
            "CredentialSuperseded(bytes32,bytes32,bytes32,uint64,uint64)");

    private TrekkeyRegistryAbi() {
    }

    static Function getIssuerKey(Hash32 issuerId, long keyVersion) {
        return new Function(
                GET_ISSUER_KEY,
                List.of(bytes32(issuerId), uint64(keyVersion, "keyVersion")),
                List.of(new TypeReference<IssuerKeyTuple>() {
                }));
    }

    static Function getBatch(Hash32 batchIdHash) {
        return new Function(
                GET_BATCH,
                List.of(bytes32(batchIdHash)),
                batchOutputParameters());
    }

    static Function getCredentialStatus(Hash32 issuerId, Hash32 credentialIdHash) {
        return new Function(
                GET_CREDENTIAL_STATUS,
                List.of(bytes32(issuerId), bytes32(credentialIdHash)),
                List.of(new TypeReference<StatusTuple>() {
                }));
    }

    static Function anchorBatch(Eip712.BatchApproval approval, Signature65 signature) {
        return new Function(
                ANCHOR_BATCH,
                List.of(new BatchApprovalTuple(approval), new DynamicBytes(signature.bytes())),
                List.of());
    }

    static Function revokeCredential(Eip712.StatusApproval approval, Signature65 signature) {
        return statusFunction(REVOKE_CREDENTIAL, approval, signature);
    }

    static Function supersedeCredential(Eip712.StatusApproval approval, Signature65 signature) {
        return statusFunction(SUPERSEDE_CREDENTIAL, approval, signature);
    }

    static String encode(Function function) {
        return FunctionEncoder.encode(function);
    }

    static BatchReadResult decodeBatchResult(List<Type> values) {
        if (values == null || values.size() != 8
                || !(values.get(0) instanceof Bytes32 issuerId)
                || !(values.get(1) instanceof Bytes32 merkleRoot)
                || !(values.get(2) instanceof Bytes32 schemaVersionHash)
                || !(values.get(3) instanceof Uint32 leafCount)
                || !(values.get(4) instanceof Uint16 treeVersion)
                || !(values.get(5) instanceof Uint64 issuerKeyVersion)
                || !(values.get(6) instanceof Uint64 anchoredAt)
                || !(values.get(7) instanceof org.web3j.abi.datatypes.Bool exists)) {
            throw new IllegalArgumentException("getBatch returned an unexpected ABI shape");
        }
        return new BatchReadResult(
                new BatchTuple(issuerId, merkleRoot, schemaVersionHash, leafCount, treeVersion, issuerKeyVersion, anchoredAt),
                exists.getValue());
    }

    static Hash32 expectedEventTopic(ChainOperationType operationType) {
        if (operationType == null) {
            throw new IllegalArgumentException("operationType must not be null");
        }
        return switch (operationType) {
            case ANCHOR_BATCH -> BATCH_ANCHORED_TOPIC;
            case REVOKE -> CREDENTIAL_REVOKED_TOPIC;
            case SUPERSEDE -> CREDENTIAL_SUPERSEDED_TOPIC;
            default -> throw new IllegalArgumentException("operation has no supported registry event: " + operationType);
        };
    }

    private static Function statusFunction(String name, Eip712.StatusApproval approval, Signature65 signature) {
        return new Function(
                name,
                List.of(new StatusApprovalTuple(approval), new DynamicBytes(signature.bytes())),
                List.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<TypeReference<?>> batchOutputParameters() {
        // BatchAnchor is an all-static tuple. Flattening it is wire-equivalent and avoids web3j 4.14's
        // incorrect offset handling when a top-level static tuple is followed by a bool.
        return (List) List.of(
                new TypeReference<Bytes32>() {
                }, new TypeReference<Bytes32>() {
                }, new TypeReference<Bytes32>() {
                }, new TypeReference<Uint32>() {
                }, new TypeReference<Uint16>() {
                }, new TypeReference<Uint64>() {
                }, new TypeReference<Uint64>() {
                }, new TypeReference<org.web3j.abi.datatypes.Bool>() {
                });
    }

    private static Bytes32 bytes32(Hash32 value) {
        if (value == null) {
            throw new IllegalArgumentException("bytes32 value must not be null");
        }
        return new Bytes32(value.bytes());
    }

    private static Uint64 uint64(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be unsigned");
        }
        return new Uint64(BigInteger.valueOf(value));
    }

    private static Hash32 topic(String eventSignature) {
        return Hashing.keccak256Utf8(eventSignature);
    }

    static final class IssuerKeyTuple extends StaticStruct {

        final Address signer;
        final Uint64 validFrom;
        final Uint64 validUntil;
        final Uint64 compromisedAt;

        public IssuerKeyTuple(Address signer, Uint64 validFrom, Uint64 validUntil, Uint64 compromisedAt) {
            super(signer, validFrom, validUntil, compromisedAt);
            this.signer = signer;
            this.validFrom = validFrom;
            this.validUntil = validUntil;
            this.compromisedAt = compromisedAt;
        }
    }

    static final class BatchTuple extends StaticStruct {

        final Bytes32 issuerId;
        final Bytes32 merkleRoot;
        final Bytes32 schemaVersionHash;
        final Uint32 leafCount;
        final Uint16 treeVersion;
        final Uint64 issuerKeyVersion;
        final Uint64 anchoredAt;

        public BatchTuple(
                Bytes32 issuerId,
                Bytes32 merkleRoot,
                Bytes32 schemaVersionHash,
                Uint32 leafCount,
                Uint16 treeVersion,
                Uint64 issuerKeyVersion,
                Uint64 anchoredAt) {
            super(issuerId, merkleRoot, schemaVersionHash, leafCount, treeVersion, issuerKeyVersion, anchoredAt);
            this.issuerId = issuerId;
            this.merkleRoot = merkleRoot;
            this.schemaVersionHash = schemaVersionHash;
            this.leafCount = leafCount;
            this.treeVersion = treeVersion;
            this.issuerKeyVersion = issuerKeyVersion;
            this.anchoredAt = anchoredAt;
        }
    }

    record BatchReadResult(BatchTuple batch, boolean exists) {
    }

    static final class StatusTuple extends StaticStruct {

        final Uint8 state;
        final Uint64 effectiveAt;
        final Uint64 recordedAt;
        final Uint64 issuerKeyVersion;
        final Bytes32 replacementCredentialIdHash;

        public StatusTuple(
                Uint8 state,
                Uint64 effectiveAt,
                Uint64 recordedAt,
                Uint64 issuerKeyVersion,
                Bytes32 replacementCredentialIdHash) {
            super(state, effectiveAt, recordedAt, issuerKeyVersion, replacementCredentialIdHash);
            this.state = state;
            this.effectiveAt = effectiveAt;
            this.recordedAt = recordedAt;
            this.issuerKeyVersion = issuerKeyVersion;
            this.replacementCredentialIdHash = replacementCredentialIdHash;
        }
    }

    private static final class BatchApprovalTuple extends StaticStruct {

        private BatchApprovalTuple(Eip712.BatchApproval approval) {
            super(
                    bytes32(approval.issuerId()),
                    bytes32(approval.batchIdHash()),
                    bytes32(approval.merkleRoot()),
                    bytes32(approval.schemaVersionHash()),
                    new Uint32(approval.leafCount()),
                    new Uint16(approval.treeVersion()),
                    new Uint64(approval.issuerKeyVersion()),
                    new Uint64(approval.approvalNonce()),
                    new Uint64(approval.deadline()));
        }
    }

    private static final class StatusApprovalTuple extends StaticStruct {

        private StatusApprovalTuple(Eip712.StatusApproval approval) {
            super(
                    bytes32(approval.issuerId()),
                    bytes32(approval.credentialIdHash()),
                    new Uint8(approval.action().value()),
                    bytes32(approval.replacementCredentialIdHash()),
                    new Uint64(approval.effectiveAt()),
                    new Uint64(approval.issuerKeyVersion()),
                    new Uint64(approval.approvalNonce()),
                    new Uint64(approval.deadline()));
        }
    }
}
