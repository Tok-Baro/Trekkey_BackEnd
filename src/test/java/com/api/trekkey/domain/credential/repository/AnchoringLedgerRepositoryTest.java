package com.api.trekkey.domain.credential.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncBatchItem;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialSource;
import com.api.trekkey.domain.credential.entity.AncCredentialStatusEvent;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.AncOutboxEvent;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.OutboxAggregateType;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AnchoringLedgerRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 12, 0);

    @Autowired
    private AncCredentialRepository credentialRepository;

    @Autowired
    private AncCredentialSourceRepository credentialSourceRepository;

    @Autowired
    private AncBatchRepository batchRepository;

    @Autowired
    private AncBatchItemRepository batchItemRepository;

    @Autowired
    private AncOutboxEventRepository outboxEventRepository;

    @Autowired
    private AncCredentialStatusEventRepository statusEventRepository;

    @Autowired
    private AncChainTransactionRepository chainTransactionRepository;

    @Test
    void findsCredentialByPublicIdAndRejectsDuplicatePublicId() {
        AncCredential credential = credentialRepository.saveAndFlush(credential("credential-public-1", 1));

        assertThat(credentialRepository.findByPublicId("credential-public-1"))
                .containsSame(credential);

        assertThatThrownBy(() -> credentialRepository.saveAndFlush(credential("credential-public-1", 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sourceFingerprintIsIdempotencyKey() {
        AncCredential first = credentialRepository.saveAndFlush(credential("credential-public-1", 1));
        byte[] fingerprint = bytes(41);
        credentialSourceRepository.saveAndFlush(AncCredentialSource.of(
                first.getId(), CredentialSourceType.TEAM, 100L, null, null, "team-public-1", fingerprint, NOW));

        AncCredential second = credentialRepository.saveAndFlush(credential("credential-public-2", 2));
        credentialSourceRepository.save(AncCredentialSource.of(
                second.getId(), CredentialSourceType.TEAM, 101L, null, null, "team-public-2", fingerprint, NOW));

        assertThatThrownBy(credentialSourceRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void batchItemCanIncludeCredentialOnlyOnce() {
        AncCredential credential = credentialRepository.saveAndFlush(credential("credential-public-1", 1));
        AncBatch firstBatch = batchRepository.saveAndFlush(batch("batch-public-1", 1));
        AncBatch secondBatch = batchRepository.saveAndFlush(batch("batch-public-2", 2));

        batchItemRepository.saveAndFlush(AncBatchItem.of(
                firstBatch.getId(), credential.getId(), 0, credential.getCredentialIdHash(), bytes(61), "[]"));
        assertThatThrownBy(() -> batchItemRepository.saveAndFlush(AncBatchItem.of(
                secondBatch.getId(), credential.getId(), 0, credential.getCredentialIdHash(), bytes(62), "[]")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outboxStateTransitionsArePersisted() {
        AncOutboxEvent event = outboxEventRepository.saveAndFlush(AncOutboxEvent.pending(
                OutboxAggregateType.CREDENTIAL,
                1L,
                "CREDENTIAL_READY",
                "outbox-key-1",
                "{}",
                NOW));

        event.claim("credential-worker-1", NOW);
        event.markProcessed(NOW.plusSeconds(1));
        outboxEventRepository.flush();

        assertThat(outboxEventRepository.findById(event.getId()))
                .get()
                .extracting(AncOutboxEvent::getStatus, AncOutboxEvent::getAttemptCount, AncOutboxEvent::getLockedBy)
                .containsExactly(OutboxStatus.PROCESSED, 1, null);
    }

    @Test
    void onlyOneStatusCorrectionCanExistForCredential() {
        AncCredential credential = credentialRepository.saveAndFlush(credential("credential-status", 3));
        AncCredentialStatusEvent first = AncCredentialStatusEvent.request(
                credential.getId(), 2L, CredentialStatus.ANCHORED, CredentialStatus.REVOKED, "ADMIN_REQUEST", null,
                3L, null, 0L, NOW.plusHours(1), "status-event-1", NOW);
        AncCredentialStatusEvent second = AncCredentialStatusEvent.request(
                credential.getId(), 2L, CredentialStatus.ANCHORED, CredentialStatus.REVOKED, "ADMIN_REQUEST", null,
                3L, null, 1L, NOW.plusHours(1), "status-event-2", NOW);

        statusEventRepository.saveAndFlush(first);
        assertThatThrownBy(() -> statusEventRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pessimisticClaimQueriesReturnOnlyEligibleRows() {
        AncCredential ready = credentialRepository.saveAndFlush(credential("credential-ready", 1));
        AncCredential batched = credentialRepository.saveAndFlush(credential("credential-batched", 2));
        batched.markBatched();

        AncOutboxEvent readyEvent = outboxEventRepository.saveAndFlush(AncOutboxEvent.pending(
                OutboxAggregateType.CREDENTIAL, ready.getId(), "CREDENTIAL_READY", "outbox-ready", "{}", NOW));
        outboxEventRepository.saveAndFlush(AncOutboxEvent.pending(
                OutboxAggregateType.CREDENTIAL, ready.getId(), "CREDENTIAL_LATER", "outbox-later", "{}", NOW.plusMinutes(1)));

        List<AncCredential> credentials = credentialRepository.findBatchClaimCandidatesForUpdate(
                CredentialStatus.READY, PageRequest.of(0, 10));
        List<AncOutboxEvent> events = outboxEventRepository.findClaimCandidatesForUpdate(
                OutboxStatus.PENDING, OutboxStatus.PROCESSING, NOW, NOW.minusMinutes(1), PageRequest.of(0, 10));

        assertThat(credentials).extracting(AncCredential::getId).containsExactly(ready.getId());
        assertThat(events).extracting(AncOutboxEvent::getId).containsExactly(readyEvent.getId());
    }

    @Test
    void claimQueryRecoversExpiredProcessingLease() {
        AncOutboxEvent stale = outboxEventRepository.saveAndFlush(AncOutboxEvent.pending(
                OutboxAggregateType.CREDENTIAL, 1L, "CREDENTIAL_READY", "outbox-stale", "{}", NOW.minusMinutes(2)));
        stale.claim("dead-worker", NOW.minusMinutes(2));
        outboxEventRepository.flush();

        List<AncOutboxEvent> events = outboxEventRepository.findClaimCandidatesForUpdate(
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING,
                NOW,
                NOW.minusMinutes(1),
                PageRequest.of(0, 10));

        assertThat(events).extracting(AncOutboxEvent::getId).containsExactly(stale.getId());
    }

    @Test
    void chainNonceReservationIsUniqueAndUnknownPollingRespectsNextAttemptAt() {
        AncChainTransaction first = chainTransactionRepository.saveAndFlush(chainTransaction("chain-1"));
        first.prepare(new byte[] {1, 2, 3}, bytes(81), 14L, bytes(91, 20), NOW);
        chainTransactionRepository.flush();

        AncChainTransaction duplicate = chainTransactionRepository.save(chainTransaction("chain-2"));
        duplicate.prepare(new byte[] {4, 5, 6}, bytes(82), 14L, bytes(91, 20), NOW);
        assertThatThrownBy(chainTransactionRepository::flush).isInstanceOf(DataIntegrityViolationException.class);

        // Re-load a separate row after the failed flush transaction is not reliable in this persistence context.
    }

    @Test
    void unknownReceiptPollingSelectsOnlyDueTransactions() {
        AncChainTransaction due = chainTransactionRepository.saveAndFlush(chainTransaction("chain-due"));
        due.prepare(new byte[] {1}, bytes(83), 15L, bytes(91, 20), NOW.minusMinutes(2));
        due.markSubmitted(NOW.minusMinutes(1));
        due.markUnknown("RPC_TIMEOUT", NOW.minusSeconds(30), NOW.minusSeconds(1));

        AncChainTransaction later = chainTransactionRepository.saveAndFlush(chainTransaction("chain-later"));
        later.prepare(new byte[] {2}, bytes(84), 16L, bytes(91, 20), NOW.minusMinutes(2));
        later.markSubmitted(NOW.minusMinutes(1));
        later.markUnknown("RPC_TIMEOUT", NOW.minusSeconds(30), NOW.plusMinutes(1));
        chainTransactionRepository.flush();

        List<AncChainTransaction> dueTransactions = chainTransactionRepository.findUnknownDueForReceipt(
                ChainTransactionStatus.UNKNOWN, NOW, PageRequest.of(0, 10));

        assertThat(dueTransactions).extracting(AncChainTransaction::getId).containsExactly(due.getId());
    }

    @Test
    void statusEventSummaryIsOrganizationScopedAndIncludesTransactionState() {
        AncCredential original = credentialRepository.saveAndFlush(
                credential(1L, "credential-original", 10));
        original.markBatched();
        original.markAnchored();
        AncCredential replacement = credentialRepository.saveAndFlush(
                credential(1L, "credential-replacement", 11));
        replacement.markBatched();
        replacement.markAnchored();
        credentialRepository.flush();

        AncCredentialStatusEvent event = AncCredentialStatusEvent.request(
                original.getId(),
                2L,
                CredentialStatus.ANCHORED,
                CredentialStatus.SUPERSEDED,
                "REISSUED",
                "corrected certificate",
                3L,
                replacement.getId(),
                1L,
                NOW.plusHours(1),
                "status-event-org-1",
                NOW);
        event.recordApproval("{\"status\":1}", bytes(51), bytes(52, 65));
        statusEventRepository.saveAndFlush(event);

        AncChainTransaction transaction = AncChainTransaction.pending(
                null,
                event.getId(),
                null,
                ChainOperationType.SUPERSEDE,
                "SUPERSEDE:credential-original",
                1001L,
                address(),
                "v1",
                null);
        transaction.markFailed("REVERTED");
        chainTransactionRepository.saveAndFlush(transaction);

        AncCredential otherOrganizationCredential = credentialRepository.saveAndFlush(
                credential(2L, "credential-other-org", 12));
        statusEventRepository.saveAndFlush(AncCredentialStatusEvent.request(
                otherOrganizationCredential.getId(),
                3L,
                CredentialStatus.ANCHORED,
                CredentialStatus.REVOKED,
                "ADMIN_REQUEST",
                null,
                4L,
                null,
                1L,
                NOW.plusHours(1),
                "status-event-org-2",
                NOW));

        List<AncCredentialStatusEventRepository.StatusEventRow> rows =
                statusEventRepository.findAllRowsByOrganizationId(1L);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(event.getId());
            assertThat(row.getCredentialPublicId()).isEqualTo("credential-original");
            assertThat(row.getCredentialNo()).isEqualTo("CERT-10");
            assertThat(row.getCredentialStatus()).isEqualTo("ANCHORED");
            assertThat(row.getPreviousStatus()).isEqualTo("ANCHORED");
            assertThat(row.getNextStatus()).isEqualTo("SUPERSEDED");
            assertThat(row.getReasonCode()).isEqualTo("REISSUED");
            assertThat(row.getSupersedingCredentialPublicId())
                    .isEqualTo("credential-replacement");
            assertThat(row.getIssuerSignature()).hasSize(65);
            assertThat(row.getTransactionStatus()).isEqualTo("FAILED");
            assertThat(row.getLastErrorCode()).isEqualTo("REVERTED");
        });
    }

    private AncCredential credential(String publicId, int seed) {
        return credential(1L, publicId, seed);
    }

    private AncCredential credential(Long organizationId, String publicId, int seed) {
        return AncCredential.ready(
                organizationId,
                publicId,
                bytes(seed),
                "CERT-" + seed,
                CredentialType.AWARD,
                "trekkey:award:v1:jcs-rfc8785:unicode-nfc-1",
                bytes(seed + 10),
                "{\"id\":\"" + publicId + "\"}",
                new byte[] {1, 2, 3},
                new byte[] {4, 5, 6},
                bytes(seed + 20),
                bytes(seed + 30),
                NOW,
                null);
    }

    private AncBatch batch(String publicId, int seed) {
        return AncBatch.seal(
                1L,
                1L,
                publicId,
                bytes(seed + 70),
                bytes(seed + 80),
                1,
                1,
                bytes(seed + 90),
                seed,
                NOW.plusDays(1),
                NOW);
    }

    private AncChainTransaction chainTransaction(String idempotencyKey) {
        return AncChainTransaction.pending(
                1L,
                null,
                null,
                ChainOperationType.ANCHOR_BATCH,
                idempotencyKey,
                1001L,
                address(),
                "v1",
                null);
    }

    private byte[] address() {
        byte[] address = new byte[20];
        address[0] = 1;
        return address;
    }

    private byte[] bytes(int value) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) value;
        return bytes;
    }

    private byte[] bytes(int value, int length) {
        byte[] bytes = new byte[length];
        bytes[0] = (byte) value;
        return bytes;
    }
}
