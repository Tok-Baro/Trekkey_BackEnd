package com.api.trekkey.domain.credential.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "anc_chain_transaction",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_anc_chain_transaction_idempotency", columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uk_anc_chain_transaction_hash", columnNames = {"chain_id", "tx_hash"}),
                @UniqueConstraint(
                        name = "uk_anc_chain_transaction_nonce",
                        columnNames = {"chain_id", "relayer_address", "tx_nonce"}),
                @UniqueConstraint(
                        name = "uk_anc_chain_transaction_event",
                        columnNames = {"chain_id", "tx_hash", "event_log_index"})
        },
        indexes = {
                @Index(name = "idx_anc_chain_transaction_batch", columnList = "batch_id"),
                @Index(name = "idx_anc_chain_transaction_status_event", columnList = "credential_status_event_id"),
                @Index(name = "idx_anc_chain_transaction_issuer_key", columnList = "issuer_key_id"),
                @Index(name = "idx_anc_chain_transaction_status_retry", columnList = "status,next_attempt_at")
        })
public class AncChainTransaction extends BaseEntity {

    @Column(name = "chain_context", length = 384, updatable = false)
    private String chainContext;

    public AncChainTransaction inContext(String context) {
        if (id != null || chainContext != null || context == null || context.isBlank() || context.length() > 384) {
            throw new IllegalStateException("transaction chain context can only be fixed before persistence");
        }
        if ((chainId == 0) != context.startsWith("SUI|")) {
            throw new IllegalStateException("transaction coordinates and chain context disagree");
        }
        chainContext = context;
        return this;
    }

    public boolean isSui() { return chainId == 0 && contractAddress != null && contractAddress.length == 32; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", updatable = false)
    private Long batchId;

    @Column(name = "credential_status_event_id", updatable = false)
    private Long credentialStatusEventId;

    @Column(name = "issuer_key_id", updatable = false)
    private Long issuerKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20, updatable = false)
    private ChainOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "chain_id", nullable = false, updatable = false)
    private long chainId;

    @Getter(AccessLevel.NONE)
    @Column(name = "contract_address", nullable = false, length = 32, updatable = false)
    private byte[] contractAddress;

    @Column(name = "contract_version", nullable = false, length = 100, updatable = false)
    private String contractVersion;

    @Getter(AccessLevel.NONE)
    @Column(name = "tx_hash", length = 32)
    private byte[] txHash;

    @Column(name = "tx_nonce")
    private Long txNonce;

    @Getter(AccessLevel.NONE)
    @Column(name = "relayer_address", length = 32)
    private byte[] relayerAddress;

    @Getter(AccessLevel.NONE)
    @jakarta.persistence.Lob
    @Column(name = "signed_raw_transaction", columnDefinition = "BLOB")
    private byte[] signedRawTransaction;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "block_number")
    private Long blockNumber;

    @Getter(AccessLevel.NONE)
    @Column(name = "block_hash", length = 32)
    private byte[] blockHash;

    @Column(name = "event_log_index")
    private Integer eventLogIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChainTransactionStatus status;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    private AncChainTransaction(
            Long batchId,
            Long credentialStatusEventId,
            Long issuerKeyId,
            ChainOperationType operationType,
            String idempotencyKey,
            long chainId,
            byte[] contractAddress,
            String contractVersion,
            LocalDateTime nextAttemptAt) {
        if (operationType == null || isBlank(idempotencyKey) || chainId < 0 || contractAddress == null
                || contractAddress.length != (chainId == 0 ? 32 : 20) || isBlank(contractVersion)) {
            throw new IllegalArgumentException("chain transaction required fields are missing");
        }
        validateTarget(operationType, batchId, credentialStatusEventId, issuerKeyId);
        this.batchId = batchId;
        this.credentialStatusEventId = credentialStatusEventId;
        this.issuerKeyId = issuerKeyId;
        this.operationType = operationType;
        this.idempotencyKey = idempotencyKey;
        this.chainId = chainId;
        this.contractAddress = contractAddress.clone();
        this.contractVersion = contractVersion;
        this.status = ChainTransactionStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
    }

    public static AncChainTransaction pending(
            Long batchId,
            Long credentialStatusEventId,
            Long issuerKeyId,
            ChainOperationType operationType,
            String idempotencyKey,
            long chainId,
            byte[] contractAddress,
            String contractVersion,
            LocalDateTime nextAttemptAt) {
        return new AncChainTransaction(
                batchId, credentialStatusEventId, issuerKeyId, operationType, idempotencyKey, chainId,
                contractAddress, contractVersion, nextAttemptAt);
    }

    public byte[] getContractAddress() {
        return copy(contractAddress);
    }

    public byte[] getTxHash() {
        return copy(txHash);
    }

    public byte[] getBlockHash() {
        return copy(blockHash);
    }

    public byte[] getSignedRawTransaction() {
        return copy(signedRawTransaction);
    }

    public byte[] getRelayerAddress() {
        return copy(relayerAddress);
    }

    public void prepare(
            byte[] signedRawTransaction,
            byte[] txHash,
            Long txNonce,
            byte[] relayerAddress,
            LocalDateTime preparedAt) {
        if (status != ChainTransactionStatus.PENDING || signedRawTransaction == null || signedRawTransaction.length == 0
                || txHash == null || txHash.length != 32 || relayerAddress == null
                || relayerAddress.length != (isSui() ? 32 : 20) || preparedAt == null
                || (isSui() ? txNonce != null : (txNonce == null || txNonce < 0))) {
            throw new IllegalStateException("chain transaction cannot be prepared");
        }
        this.signedRawTransaction = signedRawTransaction.clone();
        this.txHash = txHash.clone();
        this.txNonce = txNonce;
        this.relayerAddress = relayerAddress.clone();
        this.preparedAt = preparedAt;
        this.status = ChainTransactionStatus.PREPARED;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
    }

    public void markSubmitted(LocalDateTime submittedAt) {
        if (status != ChainTransactionStatus.PREPARED || txHash == null || (!isSui() && txNonce == null) || preparedAt == null
                || submittedAt == null || submittedAt.isBefore(preparedAt)) {
            throw new IllegalStateException("chain transaction cannot be marked submitted");
        }
        this.submittedAt = submittedAt;
        this.status = ChainTransactionStatus.SUBMITTED;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
    }

    public void markConfirmed(long blockNumber, byte[] blockHash, int eventLogIndex, LocalDateTime confirmedAt) {
        if ((status != ChainTransactionStatus.SUBMITTED && status != ChainTransactionStatus.UNKNOWN)
                || blockNumber < 0 || blockHash == null || blockHash.length != 32
                || eventLogIndex < 0 || confirmedAt == null || preparedAt == null || confirmedAt.isBefore(preparedAt)) {
            throw new IllegalStateException("chain transaction cannot be marked confirmed");
        }
        this.blockNumber = blockNumber;
        this.blockHash = blockHash.clone();
        this.eventLogIndex = eventLogIndex;
        this.confirmedAt = confirmedAt;
        this.status = ChainTransactionStatus.CONFIRMED;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
    }

    public void markUnknown(String errorCode, LocalDateTime observedAt, LocalDateTime nextAttemptAt) {
        if ((status != ChainTransactionStatus.PREPARED && status != ChainTransactionStatus.SUBMITTED)
                || isBlank(errorCode) || observedAt == null || nextAttemptAt == null || preparedAt == null
                || observedAt.isBefore(preparedAt) || nextAttemptAt.isBefore(observedAt)) {
            throw new IllegalStateException("only a prepared or submitted chain transaction can become unknown");
        }
        if (submittedAt == null) {
            submittedAt = observedAt;
        }
        this.status = ChainTransactionStatus.UNKNOWN;
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void rescheduleUnknown(String errorCode, LocalDateTime nextAttemptAt) {
        if (status != ChainTransactionStatus.UNKNOWN || isBlank(errorCode) || nextAttemptAt == null
                || submittedAt == null || nextAttemptAt.isBefore(submittedAt)) {
            throw new IllegalStateException("only an unknown chain transaction can be rescheduled");
        }
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void reschedulePending(String errorCode, LocalDateTime nextAttemptAt) {
        if (status != ChainTransactionStatus.PENDING || isBlank(errorCode) || nextAttemptAt == null) {
            throw new IllegalStateException("only a pending transaction can be rescheduled");
        }
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailed(String errorCode) {
        if ((status != ChainTransactionStatus.PENDING && status != ChainTransactionStatus.PREPARED
                && status != ChainTransactionStatus.SUBMITTED
                && status != ChainTransactionStatus.UNKNOWN) || isBlank(errorCode)) {
            throw new IllegalStateException("only a non-terminal transaction can fail");
        }
        this.status = ChainTransactionStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = null;
    }

    public void markFailed(String errorCode, LocalDateTime ignoredFailureTime) {
        if (ignoredFailureTime == null) {
            throw new IllegalStateException("failure time is required");
        }
        markFailed(errorCode);
    }

    public void resetForApprovalRenewal() {
        if (status != ChainTransactionStatus.FAILED) {
            throw new IllegalStateException("only a failed chain transaction can be reset for approval renewal");
        }
        this.txHash = null;
        this.txNonce = null;
        this.relayerAddress = null;
        this.signedRawTransaction = null;
        this.preparedAt = null;
        this.blockNumber = null;
        this.blockHash = null;
        this.eventLogIndex = null;
        this.submittedAt = null;
        this.confirmedAt = null;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
        this.status = ChainTransactionStatus.PENDING;
    }

    private static void validateTarget(
            ChainOperationType operationType, Long batchId, Long credentialStatusEventId, Long issuerKeyId) {
        int targetCount = (batchId == null ? 0 : 1)
                + (credentialStatusEventId == null ? 0 : 1)
                + (issuerKeyId == null ? 0 : 1);
        boolean typeMatches = switch (operationType) {
            case ANCHOR_BATCH -> batchId != null;
            case REVOKE, SUPERSEDE -> credentialStatusEventId != null;
            case REGISTER_KEY, RETIRE_KEY, COMPROMISE_KEY -> issuerKeyId != null;
        };
        if (targetCount != 1 || !typeMatches) {
            throw new IllegalArgumentException("chain transaction must have exactly one matching target");
        }
        if ((batchId != null && batchId <= 0) || (credentialStatusEventId != null && credentialStatusEventId <= 0)
                || (issuerKeyId != null && issuerKeyId <= 0)) {
            throw new IllegalArgumentException("chain transaction target ids must be positive");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
