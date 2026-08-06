package com.api.trekkey.domain.credential.service.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.AncOutboxEvent;
import com.api.trekkey.domain.credential.entity.BatchStatus;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.OutboxAggregateType;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BlockchainWorkTransactionsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 12, 0);

    @Mock
    private AncOutboxEventRepository outboxEventRepository;

    @Mock
    private AncChainTransactionRepository chainTransactionRepository;

    @Mock
    private AncBatchRepository batchRepository;

    @Mock
    private AncBatchItemRepository batchItemRepository;

    @Mock
    private AncCredentialRepository credentialRepository;

    @Mock
    private AncCredentialStatusEventRepository statusEventRepository;

    @Mock
    private AncIssuerKeyRepository issuerKeyRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private BlockchainProperties properties;

    @InjectMocks
    private BlockchainWorkTransactions transactions;

    @Test
    void revertedReceiptMakesProcessedOutboxRenewableAsDeadWork() {
        AncBatch batch = AncBatch.seal(
                1L, 2L, "batch-1", bytes(1, 32), bytes(2, 32), 1, 1, bytes(3, 32), 7,
                NOW.plusMinutes(15), NOW.minusMinutes(1));
        ReflectionTestUtils.setField(batch, "id", 11L);
        batch.recordApproval("{}", bytes(4, 32), bytes(5, 65), NOW);
        batch.beginAnchoring();

        AncChainTransaction transaction = AncChainTransaction.pending(
                batch.getId(), null, null, ChainOperationType.ANCHOR_BATCH, "ANCHOR_BATCH:batch-1",
                1001L, bytes(6, 20), "1", NOW);
        ReflectionTestUtils.setField(transaction, "id", 21L);
        transaction.prepare(new byte[] {1, 2, 3}, bytes(7, 32), 9L, bytes(8, 20), NOW);
        transaction.markSubmitted(NOW.plusSeconds(1));

        AncOutboxEvent outbox = AncOutboxEvent.pending(
                OutboxAggregateType.BATCH, batch.getId(), "ANCHOR_BATCH",
                "OUTBOX:ANCHOR_BATCH:batch-1", "{}", NOW);
        outbox.claim("worker-1", NOW);
        outbox.markProcessed(NOW.plusSeconds(1));

        given(chainTransactionRepository.findByIdForUpdate(transaction.getId()))
                .willReturn(Optional.of(transaction));
        given(batchRepository.findById(batch.getId())).willReturn(Optional.of(batch));
        given(outboxEventRepository.findByIdempotencyKey("OUTBOX:" + transaction.getIdempotencyKey()))
                .willReturn(Optional.of(outbox));

        transactions.failReceipt(transaction.getId(), "CHAIN_REVERTED");

        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.FAILED);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(outbox.getLastErrorCode()).isEqualTo("CHAIN_REVERTED");
    }

    private static byte[] bytes(int firstByte, int length) {
        byte[] bytes = new byte[length];
        bytes[0] = (byte) firstByte;
        return bytes;
    }
}
