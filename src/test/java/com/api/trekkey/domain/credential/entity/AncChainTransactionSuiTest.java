package com.api.trekkey.domain.credential.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.domain.credential.crypto.ChainAddress;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort.PreparedTransaction;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AncChainTransactionSuiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);
    private static final String SUI_CONTEXT = "SUI|testnet|a1b2c3d4|0x" + "11".repeat(32) + "|0x" + "22".repeat(32) + "|1";
    private static final String EVM_CONTEXT = "KAIA|1001|0x" + "11".repeat(20) + "|1";

    @Test
    void suiPreparationAndConfirmationKeepNativeSenderAndNullNonce() {
        AncChainTransaction transaction = pending(0, 32).inContext(SUI_CONTEXT);
        byte[] raw = bytes(1024, 0x33);
        byte[] hash = bytes(32, 0x44);
        byte[] sender = bytes(32, 0x55);
        transaction.prepare(raw, hash, null, sender, NOW);
        assertThat(transaction.isSui()).isTrue();
        assertThat(transaction.getChainId()).isZero();
        assertThat(transaction.getTxNonce()).isNull();
        assertThat(transaction.getContractAddress()).hasSize(32);
        assertThat(transaction.getRelayerAddress()).containsExactly(sender);
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.PREPARED);
        raw[0] = hash[0] = sender[0] = 0;
        transaction.getRelayerAddress()[1] = 0;
        assertThat(transaction.getSignedRawTransaction()).containsExactly(bytes(1024, 0x33));
        assertThat(transaction.getTxHash()).containsExactly(bytes(32, 0x44));
        assertThat(transaction.getRelayerAddress()).containsExactly(bytes(32, 0x55));
        transaction.markSubmitted(NOW.plusSeconds(1));
        transaction.markConfirmed(1234, bytes(32, 0x66), 0, NOW.plusSeconds(2));
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.CONFIRMED);
        assertThat(transaction.getBlockNumber()).isEqualTo(1234L);
        assertThat(transaction.getTxNonce()).isNull();
        assertThat(transaction.getChainContext()).isEqualTo(SUI_CONTEXT);
    }

    @Test
    void evmPreparationStillRequiresTwentyByteSenderAndNonnegativeNonce() {
        AncChainTransaction transaction = pending(1001, 20).inContext(EVM_CONTEXT);
        transaction.prepare(bytes(300, 1), bytes(32, 2), 0L, bytes(20, 3), NOW);
        transaction.markSubmitted(NOW.plusSeconds(1));
        assertThat(transaction.isSui()).isFalse();
        assertThat(transaction.getTxNonce()).isZero();
        assertThat(transaction.getRelayerAddress()).hasSize(20);
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.SUBMITTED);
        assertThatThrownBy(() -> pending(1001, 20).prepare(bytes(1, 1), bytes(32, 2), null, bytes(20, 3), NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending(1001, 20).prepare(bytes(1, 1), bytes(32, 2), -1L, bytes(20, 3), NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesSyntheticSuiNoncesAndCrossProviderAddressWidths() {
        assertThatThrownBy(() -> pending(0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending(1001, 32)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending(-1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending(0, 32).prepare(bytes(1, 1), bytes(32, 2), 0L, bytes(32, 3), NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending(0, 32).prepare(bytes(1, 1), bytes(32, 2), null, bytes(20, 3), NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending(1001, 20).prepare(bytes(1, 1), bytes(32, 2), 1L, bytes(32, 3), NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chainContextCanOnlyBeSetOnceBeforePersistenceAndMustAgreeWithProvider() {
        AncChainTransaction transaction = pending(0, 32);
        assertThat(transaction.inContext(SUI_CONTEXT)).isSameAs(transaction);
        assertThatThrownBy(() -> transaction.inContext(SUI_CONTEXT)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending(0, 32).inContext(EVM_CONTEXT)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending(1001, 20).inContext(SUI_CONTEXT)).isInstanceOf(IllegalStateException.class);
        for (String context : new String[] {null, "", " ", "x".repeat(385)}) {
            assertThatThrownBy(() -> pending(0, 32).inContext(context)).isInstanceOf(IllegalStateException.class);
        }
        AncChainTransaction persistedLegacy = pending(1001, 20);
        ReflectionTestUtils.setField(persistedLegacy, "id", 1L);
        assertThatThrownBy(() -> persistedLegacy.inContext(EVM_CONTEXT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryAndApprovalRenewalDoNotEraseRoutingIdentity() {
        AncChainTransaction transaction = pending(0, 32).inContext(SUI_CONTEXT);
        transaction.prepare(bytes(2, 1), bytes(32, 2), null, bytes(32, 3), NOW);
        transaction.markUnknown("RPC_TIMEOUT", NOW.plusSeconds(1), NOW.plusSeconds(2));
        assertThat(transaction.getTxNonce()).isNull();
        assertThat(transaction.getSignedRawTransaction()).containsExactly(bytes(2, 1));
        transaction.markFailed("EXPIRED");
        transaction.resetForApprovalRenewal();
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.PENDING);
        assertThat(transaction.getChainContext()).isEqualTo(SUI_CONTEXT);
        assertThat(transaction.getTxHash()).isNull();
        assertThat(transaction.getSignedRawTransaction()).isNull();
        assertThat(transaction.getRelayerAddress()).isNull();
        assertThatThrownBy(() -> transaction.inContext(SUI_CONTEXT.replace("testnet", "localnet")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preparedTransactionPortEnforcesSameNonceAndAddressContract() {
        byte[] raw = bytes(400, 3);
        PreparedTransaction sui = new PreparedTransaction(Hash32.of(bytes(32, 1)), null, ChainAddress.of(bytes(32, 2)), raw);
        raw[0] = 0;
        sui.signedRawTransaction()[1] = 0;
        assertThat(sui.transactionNonce()).isNull();
        assertThat(sui.relayerAddress().bytes()).hasSize(32);
        assertThat(sui.signedRawTransaction()).containsExactly(bytes(400, 3));
        PreparedTransaction legacy = new PreparedTransaction(Hash32.of(bytes(32, 1)), 7L,
            EthereumAddress.fromHex("0x" + "22".repeat(20)), bytes(2, 3));
        assertThat(legacy.transactionNonce()).isEqualTo(7L);
        assertThat(legacy.relayerAddress().bytes()).hasSize(20);
        assertThatThrownBy(() -> new PreparedTransaction(Hash32.ZERO, 0L, ChainAddress.of(bytes(32, 2)), bytes(1, 3)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparedTransaction(Hash32.ZERO, null, ChainAddress.of(bytes(20, 2)), bytes(1, 3)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static AncChainTransaction pending(long chainId, int addressBytes) {
        return AncChainTransaction.pending(1L, null, null, ChainOperationType.ANCHOR_BATCH,
            "test-transaction", chainId, bytes(addressBytes, 0x11), "1", NOW);
    }

    private static byte[] bytes(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
