package com.api.trekkey.domain.credential.service.worker;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainWorkers {

    private static final Duration MAX_EVIDENCE_RETRY_DELAY = Duration.ofSeconds(2);

    private final BlockchainWorkTransactions transactions;
    private final BlockchainAnchorPort blockchainAnchorPort;
    private final BlockchainProperties properties;
    private final Clock credentialClock;
    private final String workerId = "blockchain-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${blockchain.anchoring.receipt-polling-interval:2s}")
    public void submitPendingOutbox() {
        if (!properties.isWriteEnabled() || !properties.isWorkerEnabled()) {
            return;
        }
        Instant now = credentialClock.instant();
        transactions.claimOutbox(workerId, now).forEach(this::submit);
    }

    @Scheduled(fixedDelayString = "${blockchain.anchoring.receipt-polling-interval:2s}")
    public void reconcileReceipts() {
        if (!properties.isWriteEnabled() || !properties.isWorkerEnabled()) {
            return;
        }
        transactions.receiptTasks(credentialClock.instant()).forEach(this::reconcile);
    }

    private void submit(Long outboxId) {
        BlockchainWorkTransactions.SubmissionWork work;
        try {
            work = transactions.loadSubmission(outboxId, credentialClock.instant());
        } catch (BlockchainGatewayException exception) {
            recordPreparationFailure(outboxId, exception.getErrorCode(), exception.isRetryable());
            return;
        } catch (RuntimeException exception) {
            log.error("Could not load blockchain outbox event {}", outboxId, exception);
            recordPreparationFailure(outboxId, "WORKER_INVALID_STATE", false);
            return;
        }
        if (work == null) {
            return;
        }

        BlockchainAnchorPort.PreparedTransaction prepared = work.preparedTransaction();
        if (work.needsPreparation()) {
            try {
                prepared = work.operationType() == ChainOperationType.ANCHOR_BATCH
                        ? blockchainAnchorPort.prepareBatch(work.batchApproval(), work.issuerSignature())
                        : blockchainAnchorPort.prepareStatus(work.statusApproval(), work.issuerSignature());
            } catch (BlockchainGatewayException exception) {
                recordPreparationFailure(outboxId, exception.getErrorCode(), exception.isRetryable());
                return;
            } catch (RuntimeException exception) {
                log.error("Could not prepare blockchain transaction for outbox event {}", outboxId, exception);
                recordPreparationFailure(outboxId, "PREPARE_INVALID_STATE", false);
                return;
            }
            try {
                prepared = transactions.persistPrepared(
                        work.outboxId(), work.transactionId(), prepared, credentialClock.instant());
            } catch (DataIntegrityViolationException exception) {
                transactions.recordNonceReservationConflict(work.outboxId(), credentialClock.instant());
                return;
            } catch (RuntimeException exception) {
                log.error("Could not persist prepared blockchain transaction for outbox event {}", outboxId, exception);
                recordPreparationFailure(outboxId, "PREPARED_PERSIST_FAILED", true);
                return;
            }
        }

        try {
            blockchainAnchorPort.broadcast(prepared);
            transactions.recordBroadcastAccepted(work.outboxId(), work.transactionId(), credentialClock.instant());
        } catch (BlockchainGatewayException exception) {
            if ("BLOCKCHAIN_BROADCAST_AMBIGUOUS".equals(exception.getErrorCode())) {
                transactions.markBroadcastUnknown(
                        work.outboxId(), work.transactionId(), exception.getErrorCode(), credentialClock.instant());
            } else {
                recordPreparedBroadcastFailure(outboxId, exception.getErrorCode(), exception.isRetryable());
            }
        } catch (RuntimeException exception) {
            log.error("Ambiguous blockchain broadcast outcome for outbox event {}", outboxId, exception);
            transactions.markBroadcastUnknown(
                    work.outboxId(), work.transactionId(), "BROADCAST_RUNTIME_AMBIGUOUS", credentialClock.instant());
        }
    }

    private void recordPreparationFailure(Long outboxId, String errorCode, boolean retryable) {
        try {
            transactions.recordPreparationFailure(
                    outboxId,
                    errorCode,
                    retryable,
                    credentialClock.instant());
        } catch (RuntimeException recordException) {
            log.error("Could not record blockchain outbox failure for event {}", outboxId, recordException);
        }
    }

    private void recordPreparedBroadcastFailure(Long outboxId, String errorCode, boolean retryable) {
        try {
            transactions.recordPreparedBroadcastFailure(
                    outboxId,
                    errorCode,
                    retryable,
                    credentialClock.instant());
        } catch (RuntimeException recordException) {
            log.error("Could not record prepared blockchain broadcast failure for event {}", outboxId, recordException);
        }
    }

    private void reconcile(BlockchainWorkTransactions.ReceiptTask task) {
        try {
            BlockchainAnchorPort.ChainReceipt receipt =
                    blockchainAnchorPort.getReceipt(task.transactionHash(), task.operationType());
            switch (receipt.state()) {
                case PENDING -> handlePending(task);
                case REVERTED -> transactions.failReceipt(task.transactionId(), "CHAIN_REVERTED");
                case CONFIRMED -> confirmWithReadback(task, receipt);
            }
        } catch (BlockchainGatewayException exception) {
            if (!exception.isRetryable()) {
                transactions.failReceipt(task.transactionId(), exception.getErrorCode());
            }
        } catch (RuntimeException exception) {
            log.error("Could not reconcile blockchain transaction {}", task.transactionId(), exception);
        }
    }

    private void handlePending(BlockchainWorkTransactions.ReceiptTask task) {
        Instant now = credentialClock.instant();
        if (task.status() == com.api.trekkey.domain.credential.entity.ChainTransactionStatus.SUBMITTED
                && !now.isBefore(task.submittedAt().plus(properties.getReceiptTimeout()))) {
            transactions.markReceiptUnknown(
                    task.transactionId(),
                    "RECEIPT_TIMEOUT",
                    now,
                    now.plus(properties.getReceiptPollingInterval()));
            return;
        }
        if (task.status() == com.api.trekkey.domain.credential.entity.ChainTransactionStatus.UNKNOWN) {
            rebroadcastUnknown(task, now);
        }
    }

    private void rebroadcastUnknown(BlockchainWorkTransactions.ReceiptTask task, Instant now) {
        try {
            blockchainAnchorPort.broadcast(task.preparedTransaction());
            transactions.rescheduleUnknownReceipt(
                    task.transactionId(),
                    "UNKNOWN_REBROADCAST_PENDING",
                    now.plus(properties.getReceiptTimeout()));
        } catch (BlockchainGatewayException exception) {
            if (!exception.isRetryable()) {
                transactions.failReceipt(task.transactionId(), exception.getErrorCode());
                return;
            }
            transactions.rescheduleUnknownReceipt(
                    task.transactionId(),
                    exception.getErrorCode(),
                    now.plus(properties.getReceiptTimeout()));
        } catch (RuntimeException exception) {
            log.error("Could not rebroadcast unknown blockchain transaction {}", task.transactionId(), exception);
            transactions.rescheduleUnknownReceipt(
                    task.transactionId(),
                    "UNKNOWN_REBROADCAST_RUNTIME",
                    now.plus(properties.getReceiptTimeout()));
        }
    }

    private void confirmWithReadback(
            BlockchainWorkTransactions.ReceiptTask receiptTask,
            BlockchainAnchorPort.ChainReceipt receipt) {
        BlockchainWorkTransactions.EvidenceExpectation expectation =
                transactions.evidenceExpectation(receiptTask.transactionId());
        if (!matchesReadback(expectation)) {
            handleEvidencePending(receiptTask);
            return;
        }
        transactions.confirm(receiptTask.transactionId(), receipt, credentialClock.instant());
    }

    private void handleEvidencePending(BlockchainWorkTransactions.ReceiptTask task) {
        Instant now = credentialClock.instant();
        Instant nextCheckAt = now.plus(evidenceRetryDelay());
        if (task.status() == com.api.trekkey.domain.credential.entity.ChainTransactionStatus.SUBMITTED) {
            transactions.markReceiptUnknown(
                    task.transactionId(),
                    "CHAIN_EVIDENCE_PENDING",
                    now,
                    nextCheckAt);
            return;
        }
        transactions.rescheduleUnknownReceipt(
                task.transactionId(),
                "CHAIN_EVIDENCE_PENDING",
                nextCheckAt);
    }

    private Duration evidenceRetryDelay() {
        Duration configured = properties.getReceiptPollingInterval();
        return configured.compareTo(MAX_EVIDENCE_RETRY_DELAY) > 0
                ? MAX_EVIDENCE_RETRY_DELAY
                : configured;
    }

    private boolean matchesReadback(BlockchainWorkTransactions.EvidenceExpectation expected) {
        if (expected.operationType() == ChainOperationType.ANCHOR_BATCH) {
            BlockchainAnchorPort.OnChainBatch actual = blockchainAnchorPort.getBatch(expected.batchIdHash());
            return actual.exists()
                    && actual.issuerId().equals(expected.issuerId())
                    && actual.merkleRoot().equals(expected.merkleRoot())
                    && actual.schemaVersionHash().equals(expected.schemaVersionHash())
                    && actual.leafCount() == expected.leafCount()
                    && actual.treeVersion() == expected.treeVersion()
                    && actual.issuerKeyVersion() == expected.issuerKeyVersion();
        }
        BlockchainAnchorPort.OnChainCredentialStatus actual = blockchainAnchorPort.getCredentialStatus(
                expected.issuerId(),
                expected.credentialIdHash());
        return actual.state() == expected.credentialState()
                && actual.effectiveAt() == expected.effectiveAt()
                && actual.recordedAt() >= actual.effectiveAt()
                && actual.issuerKeyVersion() == expected.issuerKeyVersion()
                && actual.replacementCredentialIdHash().equals(expected.replacementCredentialIdHash());
    }
}
