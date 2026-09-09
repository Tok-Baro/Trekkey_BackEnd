package com.api.trekkey.domain.credential.service.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/** Exercises real SQL pagination and the worker selector together, with an isolated embedded database. */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class BlockchainReceiptSelectionRegressionTest {
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Instant OLD = NOW.minusSeconds(7200);
    private static final Instant RECENT = NOW.minusSeconds(3600);
    @Autowired private AncChainTransactionRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void submittedSuiReceiptIsSelectedAfterMoreThanOnePageOfLegacyTransactions() {
        BlockchainProperties current = sui();
        current.setWorkerClaimSize(2);
        for (int index = 1; index <= 3; index++) {
            store(kaia(), null, index, OLD.plusSeconds(index), null);
        }
        AncChainTransaction expected = store(current, current.chainContext(), 4, RECENT, null);

        assertThat(repository.findByStatusOrderByCreatedAtAsc(ChainTransactionStatus.SUBMITTED, PageRequest.of(0, 2)))
            .hasSize(2).allSatisfy(row -> assertThat(row.isSui()).isFalse());
        assertThat(worker(current).receiptTasks(NOW)).extracting(BlockchainWorkTransactions.ReceiptTask::transactionId)
            .containsExactly(expected.getId());
        assertThat(repository.findById(expected.getId()).orElseThrow().getStatus()).isEqualTo(ChainTransactionStatus.SUBMITTED);
    }

    @Test
    void unknownSuiReceiptIsSelectedAfterMoreThanOnePageOfEarlierLegacyRetries() {
        BlockchainProperties current = sui();
        current.setWorkerClaimSize(2);
        for (int index = 1; index <= 3; index++) {
            store(kaia(), null, index, OLD.plusSeconds(index), NOW.minusSeconds(1000 - index));
        }
        AncChainTransaction expected = store(current, current.chainContext(), 4, RECENT, NOW.minusSeconds(10));

        assertThat(repository.findUnknownDueForReceipt(ChainTransactionStatus.UNKNOWN, local(NOW), PageRequest.of(0, 2)))
            .hasSize(2).allSatisfy(row -> assertThat(row.isSui()).isFalse());
        assertThat(worker(current).receiptTasks(NOW)).extracting(BlockchainWorkTransactions.ReceiptTask::transactionId)
            .containsExactly(expected.getId());
        assertThat(repository.findById(expected.getId()).orElseThrow().getStatus()).isEqualTo(ChainTransactionStatus.UNKNOWN);
    }

    @Test
    void suiSelectionRejectsNullContextWrongNetworkAndMismatchedPersistedCoordinates() {
        BlockchainProperties current = sui();
        current.setWorkerClaimSize(20);
        store(current, null, 1, OLD, null);
        BlockchainProperties otherNetwork = sui();
        otherNetwork.getSui().setNetwork("localnet");
        store(otherNetwork, otherNetwork.chainContext(), 2, OLD, null);
        BlockchainProperties otherRegistry = sui();
        otherRegistry.getSui().setRegistryId("0x" + "33".repeat(32));
        store(otherRegistry, otherRegistry.chainContext(), 3, OLD, null);
        BlockchainProperties otherPackage = sui();
        otherPackage.getSui().setPackageId("0x" + "44".repeat(32));
        store(otherPackage, current.chainContext(), 4, OLD, null);
        BlockchainProperties otherVersion = sui();
        otherVersion.setContractVersion("2");
        store(otherVersion, current.chainContext(), 5, OLD, null);
        AncChainTransaction expected = store(current, current.chainContext(), 6, RECENT, null);

        assertThat(worker(current).receiptTasks(NOW)).extracting(BlockchainWorkTransactions.ReceiptTask::transactionId)
            .containsExactly(expected.getId());
    }

    @Test
    void historicalNullContextsAreEligibleOnlyForTheMatchingKaiaCoordinates() {
        BlockchainProperties current = kaia();
        current.setWorkerClaimSize(20);
        AncChainTransaction historical = store(current, null, 1, OLD, null);
        AncChainTransaction explicit = store(current, current.chainContext(), 2, RECENT, null);
        BlockchainProperties otherChain = kaia();
        otherChain.setChainId(8217);
        store(otherChain, null, 3, OLD, null);
        BlockchainProperties otherContract = kaia();
        otherContract.setContractAddress("0x" + "22".repeat(20));
        store(otherContract, null, 4, OLD, null);
        BlockchainProperties otherVersion = kaia();
        otherVersion.setContractVersion("2");
        store(otherVersion, null, 5, OLD, null);
        store(current, otherChain.chainContext(), 6, OLD, null);
        store(sui(), null, 7, OLD, null);

        assertThat(worker(current).receiptTasks(NOW)).extracting(BlockchainWorkTransactions.ReceiptTask::transactionId)
            .containsExactly(historical.getId(), explicit.getId());
    }

    @Test
    void unknownSelectionAlsoChecksEveryCoordinateAndDueTime() {
        BlockchainProperties current = kaia();
        current.setWorkerClaimSize(20);
        AncChainTransaction historical = store(current, null, 1, OLD, NOW.minusSeconds(60));
        AncChainTransaction explicit = store(current, current.chainContext(), 2, RECENT, NOW.minusSeconds(10));
        BlockchainProperties otherChain = kaia();
        otherChain.setChainId(8217);
        store(otherChain, null, 3, OLD, NOW.minusSeconds(600));
        BlockchainProperties otherContract = kaia();
        otherContract.setContractAddress("0x" + "22".repeat(20));
        store(otherContract, null, 4, OLD, NOW.minusSeconds(600));
        BlockchainProperties otherVersion = kaia();
        otherVersion.setContractVersion("2");
        store(otherVersion, null, 5, OLD, NOW.minusSeconds(600));
        store(current, otherChain.chainContext(), 6, OLD, NOW.minusSeconds(600));
        store(current, current.chainContext(), 7, OLD, NOW.plusSeconds(1));

        assertThat(worker(current).receiptTasks(NOW)).extracting(BlockchainWorkTransactions.ReceiptTask::transactionId)
            .containsExactly(historical.getId(), explicit.getId());
    }

    @Test
    void currentNetworkSubmittedAndUnknownRowsShareTheConfiguredClaimLimit() {
        BlockchainProperties current = sui();
        current.setWorkerClaimSize(3);
        AncChainTransaction first = store(current, current.chainContext(), 1, OLD, null);
        AncChainTransaction second = store(current, current.chainContext(), 2, RECENT, null);
        AncChainTransaction earliestDue = store(current, current.chainContext(), 3, OLD, NOW.minusSeconds(20));
        store(current, current.chainContext(), 4, OLD, NOW.minusSeconds(10));
        store(current, current.chainContext(), 5, OLD, NOW.plusSeconds(10));

        assertThat(worker(current).receiptTasks(NOW)).extracting(BlockchainWorkTransactions.ReceiptTask::transactionId)
            .containsExactly(first.getId(), second.getId(), earliestDue.getId());
    }

    private AncChainTransaction store(BlockchainProperties coordinates, String context, int seed,
            Instant createdAt, Instant unknownNextCheck) {
        AncChainTransaction transaction = AncChainTransaction.pending((long) seed, null, null,
            ChainOperationType.ANCHOR_BATCH, "receipt-fixture-" + seed, coordinates.ledgerChainId(),
            coordinates.ledgerContractAddress(), coordinates.getContractVersion(), local(createdAt));
        if (context != null) transaction.inContext(context);
        transaction.prepare(bytes(300, seed), bytes(32, seed), coordinates.isSui() ? null : (long) seed,
            bytes(coordinates.isSui() ? 32 : 20, 0x55), local(createdAt));
        transaction.markSubmitted(local(createdAt.plusSeconds(1)));
        if (unknownNextCheck != null) {
            transaction.markUnknown("FIXTURE_UNKNOWN", local(createdAt.plusSeconds(2)), local(unknownNextCheck));
        }
        repository.saveAndFlush(transaction);
        // Explicit chronology avoids depending on the auditing clock or insertion-order tie breaking.
        entityManager.createNativeQuery("update anc_chain_transaction set created_at = :created where id = :id")
            .setParameter("created", local(createdAt)).setParameter("id", transaction.getId()).executeUpdate();
        entityManager.clear();
        return transaction;
    }

    private BlockchainWorkTransactions worker(BlockchainProperties properties) {
        return new BlockchainWorkTransactions(mock(AncOutboxEventRepository.class), repository,
            mock(AncBatchRepository.class), mock(AncBatchItemRepository.class), mock(AncCredentialRepository.class),
            mock(AncCredentialStatusEventRepository.class), mock(AncIssuerKeyRepository.class),
            mock(OrganizationRepository.class), properties);
    }

    private static BlockchainProperties sui() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setProvider(BlockchainProperties.Provider.SUI);
        properties.getSui().setChainIdentifier("a1b2c3d4");
        properties.getSui().setPackageId("0x" + "11".repeat(32));
        properties.getSui().setRegistryId("0x" + "22".repeat(32));
        return properties;
    }

    private static BlockchainProperties kaia() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setContractAddress("0x" + "11".repeat(20));
        return properties;
    }

    private static LocalDateTime local(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }

    private static byte[] bytes(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
