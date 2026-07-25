package com.api.trekkey.domain.credential.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AnchoringLedgerEntityTest {

    private static final LocalDateTime SEALED_AT = LocalDateTime.of(2026, 7, 24, 12, 0);

    @Test
    void immutableEvidenceArraysAreCopiedOnInputAndOutput() {
        byte[] credentialIdHash = bytes(1, 32);
        byte[] schemaHash = bytes(2, 32);
        byte[] canonicalBytes = new byte[] {3, 4};
        byte[] manifestBytes = new byte[] {5, 6};
        byte[] contentHash = bytes(7, 32);
        byte[] manifestHash = bytes(8, 32);
        AncCredential credential = AncCredential.ready(
                1L, "credential-1", credentialIdHash, "CERT-1", CredentialType.AWARD, "schema-1", schemaHash,
                "{\"id\":1}", canonicalBytes, manifestBytes, contentHash, manifestHash, SEALED_AT, null);

        credentialIdHash[0] = 99;
        schemaHash[0] = 99;
        canonicalBytes[0] = 99;
        contentHash[0] = 99;
        byte[] returned = credential.getCredentialIdHash();
        returned[0] = 98;

        assertThat(credential.getCredentialIdHash()[0]).isEqualTo((byte) 1);
        assertThat(credential.getSchemaVersionHash()[0]).isEqualTo((byte) 2);
        assertThat(credential.getCanonicalBytes()[0]).isEqualTo((byte) 3);
        assertThat(credential.getFileManifestCanonicalBytes()[0]).isEqualTo((byte) 5);
        assertThat(credential.getContentHash()[0]).isEqualTo((byte) 7);
        assertThat(credential.getFileManifestHash()[0]).isEqualTo((byte) 8);

        byte[] address = bytes(9, 20);
        AncIssuerKey issuerKey = AncIssuerKey.activate(1L, 1, address, "issuer-ref", SEALED_AT, null);
        address[0] = 99;
        issuerKey.getSignerAddress()[0] = 98;
        assertThat(issuerKey.getSignerAddress()[0]).isEqualTo((byte) 9);

        AncBatch batch = AncBatch.seal(
                1L, 2L, "batch-1", bytes(10, 32), bytes(11, 32), 1, 1, bytes(12, 32), 0,
                SEALED_AT.plusHours(1), SEALED_AT);
        batch.getBatchIdHash()[0] = 98;
        assertThat(batch.getBatchIdHash()[0]).isEqualTo((byte) 10);
        assertThat(batch.getApprovalDigest()).isNull();

        AncChainTransaction transaction = AncChainTransaction.pending(
                3L, null, null, ChainOperationType.ANCHOR_BATCH, "tx-1", 1001L, bytes(13, 20), "v1", null);
        transaction.getContractAddress()[0] = 98;
        assertThat(transaction.getContractAddress()[0]).isEqualTo((byte) 13);
        assertThat(transaction.getTxHash()).isNull();
        assertThat(transaction.getBlockHash()).isNull();
    }

    @Test
    void validatesRequiredValuesSizesAndTimestampOrder() {
        assertThatThrownBy(() -> AncIssuerKey.activate(1L, 1, bytes(1, 20), "issuer", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AncCredential.ready(
                1L, " ", bytes(1, 32), "CERT-1", CredentialType.AWARD, "schema", bytes(2, 32), "{}",
                new byte[] {1}, new byte[] {2}, bytes(3, 32), bytes(4, 32), SEALED_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AncCredential.ready(
                1L, "credential-1", bytes(1, 31), "CERT-1", CredentialType.AWARD, "schema", bytes(2, 32), "{}",
                new byte[] {1}, new byte[] {2}, bytes(3, 32), bytes(4, 32), SEALED_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AncCredential.ready(
                1L, "credential-1", bytes(1, 32), "CERT-1", CredentialType.AWARD, "schema", bytes(2, 32), "{}",
                new byte[] {1}, new byte[] {2}, bytes(3, 32), bytes(4, 32), SEALED_AT, SEALED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AncBatch.seal(
                1L, 2L, "batch-1", bytes(1, 32), bytes(2, 32), 1, 1, bytes(3, 32), 0,
                SEALED_AT, SEALED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AncCredentialStatusEvent.request(
                1L, 2L, CredentialStatus.ANCHORED, CredentialStatus.REVOKED, "reason", null, 3L, null, 0,
                SEALED_AT, "event-1", SEALED_AT.plusHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AncChainTransaction.pending(
                0L, null, null, ChainOperationType.ANCHOR_BATCH, "tx-1", 1001L, bytes(1, 20), "v1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void credentialFollowsDocumentedStateMachine() {
        AncCredential credential = credential();

        credential.markBatched();
        credential.markAnchored();
        credential.markRevoked();

        assertThatThrownBy(credential::markSuperseded).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(credential::markAnchored).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedBatchRequiresAFreshApprovalBeforeItCanAnchorAgain() {
        AncBatch unsigned = batch();
        assertThatThrownBy(unsigned::markFailed).isInstanceOf(IllegalStateException.class);

        unsigned.recordApproval("{\"batch\":1}", bytes(2, 32), bytes(3, 65), SEALED_AT.plusMinutes(1));
        unsigned.beginAnchoring();
        unsigned.markFailed();
        assertThat(unsigned.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThatThrownBy(unsigned::beginAnchoring).isInstanceOf(IllegalStateException.class);

        unsigned.renewApproval(2, SEALED_AT.plusHours(2));
        assertThat(unsigned.getStatus()).isEqualTo(BatchStatus.SEALED);
        assertThat(unsigned.getIssuerSignature()).isNull();
    }

    @Test
    void chainFailureIsTerminalAndPendingRetryIsExplicit() {
        AncChainTransaction transaction = AncChainTransaction.pending(
                1L, null, null, ChainOperationType.ANCHOR_BATCH, "tx-1", 1001L, bytes(1, 20), "v1", null);
        transaction.reschedulePending("RPC_TIMEOUT", SEALED_AT.plusMinutes(1));
        transaction.prepare(new byte[] {1, 2, 3}, bytes(2, 32), 7L, bytes(3, 20), SEALED_AT.plusMinutes(2));
        transaction.markSubmitted(SEALED_AT.plusMinutes(3));
        transaction.markUnknown("CONFIRMATION_TIMEOUT", SEALED_AT.plusMinutes(4), SEALED_AT.plusMinutes(5));
        transaction.markFailed("REVERTED");

        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.FAILED);
        assertThat(transaction.getNextAttemptAt()).isNull();
        assertThatThrownBy(() -> transaction.reschedulePending("RETRY", SEALED_AT.plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownTransactionCanStillBeConfirmedByItsReceipt() {
        AncChainTransaction transaction = AncChainTransaction.pending(
                1L, null, null, ChainOperationType.ANCHOR_BATCH, "tx-unknown", 1001L, bytes(1, 20), "v1", null);
        transaction.prepare(new byte[] {1, 2, 3}, bytes(2, 32), 7L, bytes(3, 20), SEALED_AT);
        transaction.markSubmitted(SEALED_AT.plusSeconds(1));
        transaction.markUnknown("CONFIRMATION_TIMEOUT", SEALED_AT.plusMinutes(1), SEALED_AT.plusMinutes(2));
        transaction.rescheduleUnknown("UNKNOWN_REBROADCAST_PENDING", SEALED_AT.plusMinutes(4));

        transaction.markConfirmed(10L, bytes(3, 32), 0, SEALED_AT.plusMinutes(4));

        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.CONFIRMED);
    }

    @Test
    void preparedTransactionReservesTheExactRawTransactionAndNonce() {
        AncChainTransaction transaction = AncChainTransaction.pending(
                1L, null, null, ChainOperationType.ANCHOR_BATCH, "tx-prepared", 1001L, bytes(1, 20), "v1", null);
        byte[] raw = new byte[] {1, 2, 3};
        byte[] hash = bytes(2, 32);

        transaction.prepare(raw, hash, 7L, bytes(3, 20), SEALED_AT);
        raw[0] = 99;
        transaction.getSignedRawTransaction()[1] = 99;

        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.PREPARED);
        assertThat(transaction.getTxNonce()).isEqualTo(7L);
        assertThat(transaction.getSignedRawTransaction()).containsExactly(1, 2, 3);
        assertThat(transaction.getRelayerAddress()).containsExactly(bytes(3, 20));
        assertThatThrownBy(() -> transaction.prepare(
                        new byte[] {4}, bytes(3, 32), 8L, bytes(4, 20), SEALED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiredOutboxLeaseCanBeClaimedByAnotherWorker() {
        AncOutboxEvent event = AncOutboxEvent.pending(
                OutboxAggregateType.BATCH, 1L, "BATCH_ANCHOR", "outbox-lease", "{}", SEALED_AT);
        event.claim("worker-a", SEALED_AT);

        event.reclaim("worker-b", SEALED_AT.plusMinutes(2), SEALED_AT.plusMinutes(1));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getLockedBy()).isEqualTo("worker-b");
        assertThat(event.getAttemptCount()).isEqualTo(2);
    }

    private AncCredential credential() {
        return AncCredential.ready(
                1L, "credential-1", bytes(1, 32), "CERT-1", CredentialType.AWARD, "schema", bytes(2, 32), "{}",
                new byte[] {1}, new byte[] {2}, bytes(3, 32), bytes(4, 32), SEALED_AT, null);
    }

    private AncBatch batch() {
        return AncBatch.seal(
                1L, 2L, "batch-1", bytes(1, 32), bytes(2, 32), 1, 1, bytes(3, 32), 0,
                SEALED_AT.plusHours(1), SEALED_AT);
    }

    private byte[] bytes(int firstByte, int length) {
        byte[] bytes = new byte[length];
        bytes[0] = (byte) firstByte;
        return bytes;
    }
}
