package com.api.trekkey.domain.credential.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/** Embedded database mapping checks, not evidence that an existing MySQL schema has been migrated. */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AncChainTransactionSuiPersistenceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);
    private static final String SUI_CONTEXT = "SUI|testnet|a1b2c3d4|0x" + "11".repeat(32) + "|0x" + "22".repeat(32) + "|1";
    @Autowired private AncChainTransactionRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsAndReloadsSuiNullNonceAndCompleteThirtyTwoByteAddresses() {
        AncChainTransaction transaction = pending("sui-persist", 0, 32).inContext(SUI_CONTEXT);
        transaction.prepare(bytes(1024, 3), bytes(32, 4), null, bytes(32, 5), NOW);
        Long id = repository.saveAndFlush(transaction).getId();
        entityManager.clear();
        AncChainTransaction stored = repository.findById(id).orElseThrow();
        assertThat(stored.isSui()).isTrue();
        assertThat(stored.getChainId()).isZero();
        assertThat(stored.getTxNonce()).isNull();
        assertThat(stored.getChainContext()).isEqualTo(SUI_CONTEXT);
        assertThat(stored.getContractAddress()).containsExactly(bytes(32, 0x11));
        assertThat(stored.getRelayerAddress()).containsExactly(bytes(32, 5));
        assertThat(stored.getSignedRawTransaction()).containsExactly(bytes(1024, 3));
        assertThat(stored.getStatus()).isEqualTo(ChainTransactionStatus.PREPARED);
        assertStoredWidth(id, 32);
        assertThatThrownBy(() -> stored.inContext(SUI_CONTEXT)).isInstanceOf(IllegalStateException.class);
        stored.markSubmitted(NOW.plusSeconds(1));
        repository.flush();
        entityManager.clear();
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(ChainTransactionStatus.SUBMITTED);
    }

    @Test
    void persistsLegacyTwentyByteAddressesWithoutDatabasePadding() {
        AncChainTransaction transaction = pending("kaia-persist", 1001, 20);
        transaction.prepare(bytes(300, 3), bytes(32, 4), 7L, bytes(20, 5), NOW);
        Long id = repository.saveAndFlush(transaction).getId();
        entityManager.clear();
        AncChainTransaction stored = repository.findById(id).orElseThrow();
        assertThat(stored.isSui()).isFalse();
        assertThat(stored.getChainId()).isEqualTo(1001L);
        assertThat(stored.getTxNonce()).isEqualTo(7L);
        assertThat(stored.getChainContext()).isNull();
        assertThat(stored.getContractAddress()).containsExactly(bytes(20, 0x11));
        assertThat(stored.getRelayerAddress()).containsExactly(bytes(20, 5));
        assertStoredWidth(id, 20);
        assertThatThrownBy(() -> stored.inContext("KAIA|1001|0x" + "11".repeat(20) + "|1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void multipleSuiTransactionsFromSameSenderPersistWithoutInventingANonce() {
        AncChainTransaction first = pending("sui-first", 0, 32).inContext(SUI_CONTEXT);
        AncChainTransaction second = pending("sui-second", 0, 32).inContext(SUI_CONTEXT);
        first.prepare(bytes(300, 1), bytes(32, 1), null, bytes(32, 5), NOW);
        second.prepare(bytes(300, 2), bytes(32, 2), null, bytes(32, 5), NOW);
        Long firstId = repository.saveAndFlush(first).getId();
        Long secondId = repository.saveAndFlush(second).getId();
        entityManager.clear();
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(repository.findById(firstId).orElseThrow().getTxNonce()).isNull();
        assertThat(repository.findById(secondId).orElseThrow().getTxNonce()).isNull();
    }

    @Test
    void existingEvmSenderNonceUniquenessStillRejectsASecondTransaction() {
        AncChainTransaction first = pending("evm-first", 1001, 20);
        AncChainTransaction second = pending("evm-second", 1001, 20);
        first.prepare(bytes(300, 1), bytes(32, 1), 7L, bytes(20, 5), NOW);
        second.prepare(bytes(300, 2), bytes(32, 2), 7L, bytes(20, 5), NOW);
        repository.saveAndFlush(first);
        assertThatThrownBy(() -> repository.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertStoredWidth(Long id, int width) {
        Object[] lengths = (Object[]) entityManager.createNativeQuery(
            "select octet_length(contract_address), octet_length(relayer_address) from anc_chain_transaction where id = :id"
        ).setParameter("id", id).getSingleResult();
        assertThat(((Number) lengths[0]).intValue()).isEqualTo(width);
        assertThat(((Number) lengths[1]).intValue()).isEqualTo(width);
    }

    private static AncChainTransaction pending(String key, long chainId, int addressSize) {
        return AncChainTransaction.pending(1L, null, null, ChainOperationType.ANCHOR_BATCH,
            key, chainId, bytes(addressSize, 0x11), "1", NOW);
    }

    private static byte[] bytes(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
