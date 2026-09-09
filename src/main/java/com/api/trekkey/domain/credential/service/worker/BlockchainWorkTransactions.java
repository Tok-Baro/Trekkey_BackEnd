package com.api.trekkey.domain.credential.service.worker;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.ChainAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.crypto.Signature65;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialStatusEvent;
import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.AncOutboxEvent;
import com.api.trekkey.domain.credential.entity.BatchStatus;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.OutboxAggregateType;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.support.ApprovalPayloadFactory;
import com.api.trekkey.domain.credential.service.support.UtcTime;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockchainWorkTransactions {

    private final AncOutboxEventRepository outboxEventRepository;
    private final AncChainTransactionRepository chainTransactionRepository;
    private final AncBatchRepository batchRepository;
    private final AncBatchItemRepository batchItemRepository;
    private final AncCredentialRepository credentialRepository;
    private final AncCredentialStatusEventRepository statusEventRepository;
    private final AncIssuerKeyRepository issuerKeyRepository;
    private final OrganizationRepository organizationRepository;
    private final BlockchainProperties properties;

    @Transactional
    public List<Long> claimOutbox(String workerId, Instant now) {
        LocalDateTime claimedAt = UtcTime.toLocalDateTime(now);
        LocalDateTime leaseExpiredAt = UtcTime.toLocalDateTime(now.minus(properties.getOutboxLeaseTimeout()));
        List<AncOutboxEvent> events = outboxEventRepository.findClaimCandidatesForUpdate(
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING,
                claimedAt,
                leaseExpiredAt,
                PageRequest.of(0, Math.max(1, properties.getWorkerClaimSize())));
        events.forEach(event -> {
            if (event.getStatus() == OutboxStatus.PENDING) {
                event.claim(workerId, claimedAt);
            } else {
                event.reclaim(workerId, claimedAt, leaseExpiredAt);
            }
        });
        return events.stream().map(AncOutboxEvent::getId).toList();
    }

    /**
     * Loads an already claimed job. This transaction never calls RPC and never creates a raw transaction.
     */
    @Transactional
    public SubmissionWork loadSubmission(Long outboxId, Instant now) {
        AncOutboxEvent outbox = processingOutbox(outboxId);
        AncChainTransaction transaction = chainTransaction(outbox);
        if (!currentNetwork(transaction)) {
            // Never execute an old network's durable work under new provider settings.
            outbox.reschedule("CHAIN_CONTEXT_MISMATCH", UtcTime.toLocalDateTime(now.plusSeconds(3600)));
            return null;
        }
        if (transaction.getStatus() == ChainTransactionStatus.PREPARED) {
            return SubmissionWork.prepared(outbox.getId(), transaction.getId(), transaction.getOperationType(), prepared(transaction));
        }
        if (transaction.getStatus() == ChainTransactionStatus.SUBMITTED
                || transaction.getStatus() == ChainTransactionStatus.UNKNOWN
                || transaction.getStatus() == ChainTransactionStatus.CONFIRMED) {
            outbox.markProcessed(UtcTime.toLocalDateTime(now));
            return null;
        }
        if (transaction.getStatus() == ChainTransactionStatus.FAILED) {
            outbox.markDead("CHAIN_TRANSACTION_FAILED");
            return null;
        }
        if (transaction.getStatus() != ChainTransactionStatus.PENDING) {
            throw new IllegalStateException("chain transaction is not submit-ready");
        }
        return buildSubmissionWork(outbox, transaction);
    }

    /**
     * Reserves the exact relayer nonce and raw transaction before the worker is allowed to broadcast it.
     */
    @Transactional
    public BlockchainAnchorPort.PreparedTransaction persistPrepared(
            Long outboxId,
            Long transactionId,
            BlockchainAnchorPort.PreparedTransaction prepared,
            Instant preparedAt) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared transaction is required");
        }
        AncOutboxEvent outbox = processingOutbox(outboxId);
        AncChainTransaction transaction = chainTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        if (!transaction.getId().equals(chainTransaction(outbox).getId())) {
            throw new IllegalStateException("outbox does not own the chain transaction");
        }
        requireCurrentNetwork(transaction);
        if (transaction.getStatus() == ChainTransactionStatus.PREPARED) {
            if (!Hash32.of(transaction.getTxHash()).equals(prepared.transactionHash())
                    || !java.util.Objects.equals(transaction.getTxNonce(), prepared.transactionNonce())
                    || !java.util.Arrays.equals(transaction.getSignedRawTransaction(), prepared.signedRawTransaction())
                    || !java.util.Arrays.equals(transaction.getRelayerAddress(), prepared.relayerAddress().bytes())) {
                throw new IllegalStateException("prepared transaction does not match the persisted reservation");
            }
            return prepared(transaction);
        }
        if (transaction.getStatus() != ChainTransactionStatus.PENDING) {
            throw new IllegalStateException("only a pending chain transaction can reserve a nonce");
        }
        if (transaction.getOperationType() == ChainOperationType.ANCHOR_BATCH) {
            AncBatch batch = batchRepository.findById(transaction.getBatchId())
                    .orElseThrow(() -> new IllegalStateException("batch does not exist"));
            if (batch.getStatus() == BatchStatus.SIGNED) {
                batch.beginAnchoring();
            }
            if (batch.getStatus() != BatchStatus.ANCHORING) {
                throw new IllegalStateException("batch is not ready for anchoring");
            }
        }
        transaction.prepare(
                prepared.signedRawTransaction(),
                prepared.transactionHash().bytes(),
                prepared.transactionNonce(),
                prepared.relayerAddress().bytes(),
                UtcTime.toLocalDateTime(preparedAt));
        return prepared(transaction);
    }

    @Transactional
    public void recordPreparationFailure(Long outboxId, String errorCode, boolean retryable, Instant failedAt) {
        AncOutboxEvent outbox = processingOutbox(outboxId);
        AncChainTransaction transaction = chainTransaction(outbox);
        if (transaction.getStatus() != ChainTransactionStatus.PENDING) {
            return;
        }
        rescheduleOrFailPending(outbox, transaction, errorCode, retryable, failedAt);
    }

    @Transactional
    public void recordNonceReservationConflict(Long outboxId, Instant failedAt) {
        recordPreparationFailure(outboxId, "RELAYER_NONCE_RESERVED", true, failedAt);
    }

    @Transactional
    public void recordBroadcastAccepted(Long outboxId, Long transactionId, Instant submittedAt) {
        AncOutboxEvent outbox = processingOutbox(outboxId);
        AncChainTransaction transaction = chainTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        transaction.markSubmitted(UtcTime.toLocalDateTime(submittedAt));
        outbox.markProcessed(UtcTime.toLocalDateTime(submittedAt));
    }

    @Transactional
    public void markBroadcastUnknown(Long outboxId, Long transactionId, String errorCode, Instant observedAt) {
        AncOutboxEvent outbox = processingOutbox(outboxId);
        AncChainTransaction transaction = chainTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        LocalDateTime observed = UtcTime.toLocalDateTime(observedAt);
        transaction.markUnknown(errorCode, observed, observed.plus(properties.getReceiptPollingInterval()));
        outbox.markProcessed(observed);
    }

    @Transactional
    public void recordPreparedBroadcastFailure(Long outboxId, String errorCode, boolean retryable, Instant failedAt) {
        AncOutboxEvent outbox = processingOutbox(outboxId);
        AncChainTransaction transaction = chainTransaction(outbox);
        if (transaction.getStatus() != ChainTransactionStatus.PREPARED) {
            return;
        }
        boolean exhausted = outbox.getAttemptCount() >= properties.getWorkerMaxAttempts();
        if (retryable && !exhausted) {
            outbox.reschedule(errorCode, retryAt(outbox, failedAt));
            return;
        }
        transaction.markFailed(errorCode);
        markBatchFailed(transaction);
        outbox.markDead(errorCode);
    }

    @Transactional(readOnly = true)
    public List<ReceiptTask> receiptTasks(Instant now) {
        int claimSize = Math.max(1, properties.getWorkerClaimSize());
        String context = properties.chainContext();
        boolean allowLegacy = properties.getProvider() == BlockchainProperties.Provider.KAIA;
        long chainId = properties.ledgerChainId();
        byte[] contractAddress = properties.ledgerContractAddress();
        String contractVersion = properties.getContractVersion();
        List<ReceiptTask> tasks = new ArrayList<>();
        // Scope before LIMIT: old networks' unfinished rows must not starve this worker forever.
        chainTransactionRepository.findSubmittedForReceiptInContext(
                        ChainTransactionStatus.SUBMITTED,
                        context, allowLegacy, chainId, contractAddress, contractVersion,
                        PageRequest.of(0, claimSize))
                .stream()
                .map(this::receiptTask)
                .forEach(tasks::add);
        int remaining = claimSize - tasks.size();
        if (remaining > 0) {
            chainTransactionRepository.findUnknownDueForReceiptInContext(
                            ChainTransactionStatus.UNKNOWN,
                            UtcTime.toLocalDateTime(now),
                            context, allowLegacy, chainId, contractAddress, contractVersion,
                            PageRequest.of(0, remaining))
                    .stream()
                    .map(this::receiptTask)
                    .forEach(tasks::add);
        }
        return List.copyOf(tasks);
    }

    @Transactional(readOnly = true)
    public EvidenceExpectation evidenceExpectation(Long transactionId) {
        AncChainTransaction transaction = chainTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        if (transaction.getOperationType() == ChainOperationType.ANCHOR_BATCH) {
            AncBatch batch = batchRepository.findById(transaction.getBatchId())
                    .orElseThrow(() -> new IllegalStateException("batch does not exist"));
            Organization organization = organizationRepository.findById(batch.getIssuerOrganizationId())
                    .orElseThrow(() -> new IllegalStateException("organization does not exist"));
            AncIssuerKey key = issuerKeyRepository.findById(batch.getIssuerKeyId())
                    .orElseThrow(() -> new IllegalStateException("issuer key does not exist"));
            return EvidenceExpectation.batch(
                    transactionId,
                    Hash32.of(batch.getBatchIdHash()),
                    Hashing.issuerId(organizationPublicId(organization)),
                    Hash32.of(batch.getMerkleRoot()),
                    Hash32.of(batch.getSchemaVersionHash()),
                    batch.getLeafCount(),
                    batch.getTreeVersion(),
                    key.getKeyVersion());
        }

        AncCredentialStatusEvent event = statusEventRepository.findById(transaction.getCredentialStatusEventId())
                .orElseThrow(() -> new IllegalStateException("status event does not exist"));
        AncCredential credential = credentialRepository.findById(event.getCredentialId())
                .orElseThrow(() -> new IllegalStateException("credential does not exist"));
        Organization organization = organizationRepository.findById(credential.getIssuerOrganizationId())
                .orElseThrow(() -> new IllegalStateException("organization does not exist"));
        Hash32 replacementHash = event.getSupersedingCredentialId() == null
                ? Hash32.ZERO
                : Hash32.of(credentialRepository.findById(event.getSupersedingCredentialId())
                        .orElseThrow(() -> new IllegalStateException("replacement credential does not exist"))
                        .getCredentialIdHash());
        BlockchainAnchorPort.CredentialChainState expectedState = event.getNextStatus() == CredentialStatus.REVOKED
                ? BlockchainAnchorPort.CredentialChainState.REVOKED
                : BlockchainAnchorPort.CredentialChainState.SUPERSEDED;
        AncIssuerKey issuerKey = issuerKeyRepository.findById(event.getIssuerKeyId())
                .orElseThrow(() -> new IllegalStateException("issuer key does not exist"));
        return EvidenceExpectation.status(
                transactionId,
                Hashing.issuerId(organizationPublicId(organization)),
                Hash32.of(credential.getCredentialIdHash()),
                expectedState,
                UtcTime.toInstant(event.getEffectiveAt()).getEpochSecond(),
                issuerKey.getKeyVersion(),
                replacementHash);
    }

    @Transactional
    public void confirm(Long transactionId, BlockchainAnchorPort.ChainReceipt receipt, Instant confirmedAt) {
        AncChainTransaction transaction = chainTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        if (transaction.getStatus() == ChainTransactionStatus.CONFIRMED) {
            return;
        }
        transaction.markConfirmed(
                receipt.blockNumber(),
                receipt.blockHash().bytes(),
                receipt.eventLogIndex(),
                UtcTime.toLocalDateTime(confirmedAt));
        if (transaction.getOperationType() == ChainOperationType.ANCHOR_BATCH) {
            AncBatch batch = batchRepository.findById(transaction.getBatchId())
                    .orElseThrow(() -> new IllegalStateException("batch does not exist"));
            if (batch.getStatus() == BatchStatus.ANCHORING) {
                batch.markAnchored();
            }
            batchItemRepository.findByBatchIdOrderByLeafIndexAsc(batch.getId()).forEach(item -> {
                AncCredential credential = credentialRepository.findById(item.getCredentialId())
                        .orElseThrow(() -> new IllegalStateException("credential does not exist"));
                if (credential.getStatus() == CredentialStatus.BATCHED) {
                    credential.markAnchored();
                }
            });
            return;
        }
        AncCredentialStatusEvent event = statusEventRepository.findById(transaction.getCredentialStatusEventId())
                .orElseThrow(() -> new IllegalStateException("status event does not exist"));
        AncCredential credential = credentialRepository.findById(event.getCredentialId())
                .orElseThrow(() -> new IllegalStateException("credential does not exist"));
        if (credential.getStatus() == CredentialStatus.ANCHORED) {
            if (event.getNextStatus() == CredentialStatus.REVOKED) {
                credential.markRevoked();
            } else {
                credential.markSuperseded();
            }
        }
    }

    @Transactional
    public void markReceiptUnknown(Long transactionId, String errorCode, Instant observedAt, Instant nextCheckAt) {
        AncChainTransaction transaction = chainTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        if (transaction.getStatus() == ChainTransactionStatus.SUBMITTED) {
            transaction.markUnknown(
                    errorCode,
                    UtcTime.toLocalDateTime(observedAt),
                    UtcTime.toLocalDateTime(nextCheckAt));
        }
    }

    @Transactional
    public void rescheduleUnknownReceipt(Long transactionId, String errorCode, Instant nextCheckAt) {
        AncChainTransaction transaction = chainTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        if (transaction.getStatus() == ChainTransactionStatus.UNKNOWN) {
            transaction.rescheduleUnknown(errorCode, UtcTime.toLocalDateTime(nextCheckAt));
        }
    }

    @Transactional
    public void failReceipt(Long transactionId, String errorCode) {
        AncChainTransaction transaction = chainTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("chain transaction does not exist"));
        if (transaction.getStatus() == ChainTransactionStatus.CONFIRMED
                || transaction.getStatus() == ChainTransactionStatus.FAILED) {
            return;
        }
        transaction.markFailed(errorCode);
        markBatchFailed(transaction);
        outboxEventRepository.findByIdempotencyKey("OUTBOX:" + transaction.getIdempotencyKey())
                .filter(outbox -> outbox.getStatus() == OutboxStatus.PROCESSED)
                .ifPresent(outbox -> outbox.markDeliveryFailed(errorCode));
    }

    private SubmissionWork buildSubmissionWork(AncOutboxEvent outbox, AncChainTransaction transaction) {
        requireCurrentNetwork(transaction);
        if (transaction.getOperationType() == ChainOperationType.ANCHOR_BATCH) {
            AncBatch batch = batchRepository.findById(transaction.getBatchId())
                    .orElseThrow(() -> new IllegalStateException("batch does not exist"));
            if (batch.getStatus() != BatchStatus.SIGNED && batch.getStatus() != BatchStatus.ANCHORING) {
                throw new IllegalStateException("batch is not ready for anchoring");
            }
            AncIssuerKey issuerKey = issuerKeyRepository.findById(batch.getIssuerKeyId())
                    .orElseThrow(() -> new IllegalStateException("issuer key does not exist"));
            Organization organization = organizationRepository.findById(batch.getIssuerOrganizationId())
                    .orElseThrow(() -> new IllegalStateException("organization does not exist"));
            requireContext(batch.getChainContext());
            requireContext(issuerKey.getChainContext());
            Eip712.BatchApproval approval = ApprovalPayloadFactory.batch(organizationPublicId(organization), batch, issuerKey);
            if (!java.util.Arrays.equals(ApprovalPayloadFactory.batchDigest(properties, approval).bytes(), batch.getApprovalDigest())) {
                throw new IllegalStateException("persisted batch approval digest does not match immutable network context");
            }
            return SubmissionWork.batch(
                    outbox.getId(),
                    transaction.getId(),
                    approval,
                    Signature65.of(batch.getIssuerSignature()));
        }
        AncCredentialStatusEvent event = statusEventRepository.findById(transaction.getCredentialStatusEventId())
                .orElseThrow(() -> new IllegalStateException("status event does not exist"));
        AncCredential credential = credentialRepository.findById(event.getCredentialId())
                .orElseThrow(() -> new IllegalStateException("credential does not exist"));
        AncIssuerKey issuerKey = issuerKeyRepository.findById(event.getIssuerKeyId())
                .orElseThrow(() -> new IllegalStateException("issuer key does not exist"));
        AncCredential replacement = event.getSupersedingCredentialId() == null
                ? null
                : credentialRepository.findById(event.getSupersedingCredentialId())
                        .orElseThrow(() -> new IllegalStateException("replacement credential does not exist"));
        Organization organization = organizationRepository.findById(credential.getIssuerOrganizationId())
                .orElseThrow(() -> new IllegalStateException("organization does not exist"));
        requireContext(event.getChainContext());
        requireContext(issuerKey.getChainContext());
        Eip712.StatusApproval approval = ApprovalPayloadFactory.status(
                organizationPublicId(organization), event, credential, issuerKey, replacement);
        if (!java.util.Arrays.equals(ApprovalPayloadFactory.statusDigest(properties, approval).bytes(), event.getApprovalDigest())) {
            throw new IllegalStateException("persisted status approval digest does not match immutable network context");
        }
        return SubmissionWork.status(
                outbox.getId(),
                transaction.getId(),
                transaction.getOperationType(),
                ApprovalPayloadFactory.status(
                        organizationPublicId(organization),
                        event,
                        credential,
                        issuerKey,
                        replacement),
                Signature65.of(event.getIssuerSignature()));
    }

    private AncOutboxEvent processingOutbox(Long outboxId) {
        AncOutboxEvent outbox = outboxEventRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new IllegalStateException("outbox event does not exist"));
        if (outbox.getStatus() != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("outbox event is not processing");
        }
        return outbox;
    }

    private AncChainTransaction chainTransaction(AncOutboxEvent outbox) {
        if (outbox.getAggregateType() == OutboxAggregateType.BATCH) {
            return chainTransactionRepository.findByBatchIdAndOperationType(
                            outbox.getAggregateId(), ChainOperationType.ANCHOR_BATCH)
                    .orElseThrow(() -> new IllegalStateException("batch chain transaction does not exist"));
        }
        AncCredentialStatusEvent event = statusEventRepository.findById(outbox.getAggregateId())
                .orElseThrow(() -> new IllegalStateException("status event does not exist"));
        ChainOperationType operation = event.getNextStatus() == CredentialStatus.REVOKED
                ? ChainOperationType.REVOKE
                : ChainOperationType.SUPERSEDE;
        return chainTransactionRepository.findByCredentialStatusEventIdAndOperationType(outbox.getAggregateId(), operation)
                .orElseThrow(() -> new IllegalStateException("status chain transaction does not exist"));
    }

    private BlockchainAnchorPort.PreparedTransaction prepared(AncChainTransaction transaction) {
        return new BlockchainAnchorPort.PreparedTransaction(
                Hash32.of(transaction.getTxHash()),
                transaction.getTxNonce(),
                ChainAddress.of(transaction.getRelayerAddress()),
                transaction.getSignedRawTransaction());
    }

    private boolean currentNetwork(AncChainTransaction transaction) {
        return properties.matchesContext(transaction.getChainContext())
                && transaction.getChainId() == properties.ledgerChainId()
                && java.util.Arrays.equals(transaction.getContractAddress(), properties.ledgerContractAddress())
                && transaction.getContractVersion().equals(properties.getContractVersion());
    }

    private void requireCurrentNetwork(AncChainTransaction transaction) {
        if (!currentNetwork(transaction)) throw new IllegalStateException("transaction network context does not match this worker");
    }

    private void requireContext(String context) {
        if (!properties.matchesContext(context)) throw new IllegalStateException("approval network context does not match this worker");
    }

    private ReceiptTask receiptTask(AncChainTransaction transaction) {
        return new ReceiptTask(
                transaction.getId(),
                Hash32.of(transaction.getTxHash()),
                transaction.getOperationType(),
                UtcTime.toInstant(transaction.getSubmittedAt()),
                transaction.getStatus(),
                prepared(transaction));
    }

    private void rescheduleOrFailPending(
            AncOutboxEvent outbox,
            AncChainTransaction transaction,
            String errorCode,
            boolean retryable,
            Instant failedAt) {
        boolean exhausted = outbox.getAttemptCount() >= properties.getWorkerMaxAttempts();
        if (retryable && !exhausted) {
            LocalDateTime retryAt = retryAt(outbox, failedAt);
            transaction.reschedulePending(errorCode, retryAt);
            outbox.reschedule(errorCode, retryAt);
            return;
        }
        transaction.markFailed(errorCode);
        markBatchFailed(transaction);
        outbox.markDead(errorCode);
    }

    private LocalDateTime retryAt(AncOutboxEvent outbox, Instant now) {
        return UtcTime.toLocalDateTime(now.plusSeconds(backoffSeconds(outbox.getAttemptCount())));
    }

    private void markBatchFailed(AncChainTransaction transaction) {
        if (transaction.getOperationType() != ChainOperationType.ANCHOR_BATCH) {
            return;
        }
        AncBatch batch = batchRepository.findById(transaction.getBatchId())
                .orElseThrow(() -> new IllegalStateException("batch does not exist"));
        if (batch.getStatus() == BatchStatus.SIGNED || batch.getStatus() == BatchStatus.ANCHORING) {
            batch.markFailed();
        }
    }

    private long backoffSeconds(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 8);
        return Math.min(1L << exponent, 300L);
    }

    private String organizationPublicId(Organization organization) {
        String publicId = organization.getPublicId();
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalStateException("organization public ID is missing");
        }
        return publicId;
    }

    public record SubmissionWork(
            Long outboxId,
            Long transactionId,
            ChainOperationType operationType,
            Eip712.BatchApproval batchApproval,
            Eip712.StatusApproval statusApproval,
            Signature65 issuerSignature,
            BlockchainAnchorPort.PreparedTransaction preparedTransaction) {

        static SubmissionWork batch(
                Long outboxId, Long transactionId, Eip712.BatchApproval approval, Signature65 signature) {
            return new SubmissionWork(
                    outboxId, transactionId, ChainOperationType.ANCHOR_BATCH, approval, null, signature, null);
        }

        static SubmissionWork status(
                Long outboxId,
                Long transactionId,
                ChainOperationType operationType,
                Eip712.StatusApproval approval,
                Signature65 signature) {
            return new SubmissionWork(outboxId, transactionId, operationType, null, approval, signature, null);
        }

        static SubmissionWork prepared(
                Long outboxId,
                Long transactionId,
                ChainOperationType operationType,
                BlockchainAnchorPort.PreparedTransaction prepared) {
            return new SubmissionWork(outboxId, transactionId, operationType, null, null, null, prepared);
        }

        public boolean needsPreparation() {
            return preparedTransaction == null;
        }
    }

    public record ReceiptTask(
            Long transactionId,
            Hash32 transactionHash,
            ChainOperationType operationType,
            Instant submittedAt,
            ChainTransactionStatus status,
            BlockchainAnchorPort.PreparedTransaction preparedTransaction) {
    }

    public record EvidenceExpectation(
            Long transactionId,
            ChainOperationType operationType,
            Hash32 issuerId,
            Hash32 batchIdHash,
            Hash32 credentialIdHash,
            Hash32 merkleRoot,
            Hash32 schemaVersionHash,
            long leafCount,
            int treeVersion,
            long issuerKeyVersion,
            BlockchainAnchorPort.CredentialChainState credentialState,
            long effectiveAt,
            Hash32 replacementCredentialIdHash) {

        static EvidenceExpectation batch(
                Long transactionId,
                Hash32 batchIdHash,
                Hash32 issuerId,
                Hash32 merkleRoot,
                Hash32 schemaVersionHash,
                long leafCount,
                int treeVersion,
                long issuerKeyVersion) {
            return new EvidenceExpectation(
                    transactionId,
                    ChainOperationType.ANCHOR_BATCH,
                    issuerId,
                    batchIdHash,
                    null,
                    merkleRoot,
                    schemaVersionHash,
                    leafCount,
                    treeVersion,
                    issuerKeyVersion,
                    null,
                    0,
                    null);
        }

        static EvidenceExpectation status(
                Long transactionId,
                Hash32 issuerId,
                Hash32 credentialIdHash,
                BlockchainAnchorPort.CredentialChainState state,
                long effectiveAt,
                long issuerKeyVersion,
                Hash32 replacementCredentialIdHash) {
            ChainOperationType operation = state == BlockchainAnchorPort.CredentialChainState.REVOKED
                    ? ChainOperationType.REVOKE
                    : ChainOperationType.SUPERSEDE;
            return new EvidenceExpectation(
                    transactionId,
                    operation,
                    issuerId,
                    null,
                    credentialIdHash,
                    null,
                    null,
                    0,
                    0,
                    issuerKeyVersion,
                    state,
                    effectiveAt,
                    replacementCredentialIdHash);
        }
    }
}
