package com.api.trekkey.domain.credential.service.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainWorkersTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Mock
    private BlockchainWorkTransactions transactions;

    @Mock
    private BlockchainAnchorPort blockchainAnchorPort;

    private BlockchainProperties properties;
    private BlockchainWorkers workers;

    @BeforeEach
    void setUp() {
        properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
        properties.setWorkerEnabled(true);
        workers = new BlockchainWorkers(
                transactions,
                blockchainAnchorPort,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void stalePreparedOutboxRebroadcastsTheExactPersistedRawTransaction() {
        byte[] raw = new byte[] {1, 2, 3};
        BlockchainAnchorPort.PreparedTransaction prepared = new BlockchainAnchorPort.PreparedTransaction(
                Hash32.of(bytes(9)), 17L, EthereumAddress.fromBytes(address(1)), raw);
        raw[0] = 99;
        BlockchainWorkTransactions.SubmissionWork work = BlockchainWorkTransactions.SubmissionWork.prepared(
                11L, 22L, ChainOperationType.ANCHOR_BATCH, prepared);
        given(transactions.claimOutbox(any(), eq(NOW))).willReturn(List.of(11L));
        given(transactions.loadSubmission(11L, NOW)).willReturn(work);

        workers.submitPendingOutbox();

        ArgumentCaptor<BlockchainAnchorPort.PreparedTransaction> captured =
                ArgumentCaptor.forClass(BlockchainAnchorPort.PreparedTransaction.class);
        then(blockchainAnchorPort).should().broadcast(captured.capture());
        assertThat(captured.getValue().signedRawTransaction()).containsExactly(1, 2, 3);
        then(transactions).should().recordBroadcastAccepted(11L, 22L, NOW);
        then(blockchainAnchorPort).shouldHaveNoMoreInteractions();
    }

    @Test
    void responseLossAfterPreparedBroadcastBecomesUnknownInsteadOfPreparingAnotherNonce() {
        BlockchainAnchorPort.PreparedTransaction prepared = new BlockchainAnchorPort.PreparedTransaction(
                Hash32.of(bytes(10)), 18L, EthereumAddress.fromBytes(address(1)), new byte[] {4, 5, 6});
        BlockchainWorkTransactions.SubmissionWork work = BlockchainWorkTransactions.SubmissionWork.prepared(
                12L, 23L, ChainOperationType.REVOKE, prepared);
        given(transactions.claimOutbox(any(), eq(NOW))).willReturn(List.of(12L));
        given(transactions.loadSubmission(12L, NOW)).willReturn(work);
        doThrow(new BlockchainGatewayException(
                        "BLOCKCHAIN_BROADCAST_AMBIGUOUS", true, "response lost"))
                .when(blockchainAnchorPort)
                .broadcast(prepared);

        workers.submitPendingOutbox();

        then(transactions).should().markBroadcastUnknown(
                12L, 23L, "BLOCKCHAIN_BROADCAST_AMBIGUOUS", NOW);
        then(blockchainAnchorPort).shouldHaveNoMoreInteractions();
    }

    @Test
    void unknownReceiptMissRebroadcastsThePersistedRawTransactionAndBacksOff() {
        BlockchainAnchorPort.PreparedTransaction prepared = new BlockchainAnchorPort.PreparedTransaction(
                Hash32.of(bytes(11)), 19L, EthereumAddress.fromBytes(address(2)), new byte[] {7, 8, 9});
        BlockchainWorkTransactions.ReceiptTask task = new BlockchainWorkTransactions.ReceiptTask(
                24L,
                prepared.transactionHash(),
                ChainOperationType.ANCHOR_BATCH,
                NOW.minusSeconds(180),
                ChainTransactionStatus.UNKNOWN,
                prepared);
        given(transactions.receiptTasks(NOW)).willReturn(List.of(task));
        given(blockchainAnchorPort.getReceipt(task.transactionHash(), task.operationType()))
                .willReturn(BlockchainAnchorPort.ChainReceipt.pending());

        workers.reconcileReceipts();

        then(blockchainAnchorPort).should().broadcast(prepared);
        then(transactions).should().rescheduleUnknownReceipt(
                task.transactionId(),
                "UNKNOWN_REBROADCAST_PENDING",
                NOW.plus(properties.getReceiptTimeout()));
    }

    @Test
    void deterministicUnknownRebroadcastRejectionBecomesRenewableFailedWork() {
        BlockchainAnchorPort.PreparedTransaction prepared = new BlockchainAnchorPort.PreparedTransaction(
                Hash32.of(bytes(12)), 20L, EthereumAddress.fromBytes(address(3)), new byte[] {10, 11, 12});
        BlockchainWorkTransactions.ReceiptTask task = new BlockchainWorkTransactions.ReceiptTask(
                25L,
                prepared.transactionHash(),
                ChainOperationType.REVOKE,
                NOW.minusSeconds(180),
                ChainTransactionStatus.UNKNOWN,
                prepared);
        given(transactions.receiptTasks(NOW)).willReturn(List.of(task));
        given(blockchainAnchorPort.getReceipt(task.transactionHash(), task.operationType()))
                .willReturn(BlockchainAnchorPort.ChainReceipt.pending());
        doThrow(new BlockchainGatewayException(
                        "BLOCKCHAIN_BROADCAST_REJECTED", false, "deterministic rejection"))
                .when(blockchainAnchorPort)
                .broadcast(prepared);

        workers.reconcileReceipts();

        then(transactions).should().failReceipt(task.transactionId(), "BLOCKCHAIN_BROADCAST_REJECTED");
    }

    private static byte[] bytes(int seed) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) seed;
        return bytes;
    }

    private static byte[] address(int seed) {
        byte[] bytes = new byte[20];
        bytes[0] = (byte) seed;
        return bytes;
    }
}
