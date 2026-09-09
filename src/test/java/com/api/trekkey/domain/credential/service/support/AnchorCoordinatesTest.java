package com.api.trekkey.domain.credential.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.SuiDigest;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import org.junit.jupiter.api.Test;

class AnchorCoordinatesTest {
    private static final String PACKAGE = "0x" + "11".repeat(32);
    private static final String REGISTRY = "0x" + "22".repeat(32);
    private static final String KAIA_CONTRACT = "0x" + "33".repeat(20);

    @Test
    void unbatchedSuiPendingReportsIntentWithoutAdvertisingUnpersistedCoordinatesOrApproval() {
        BlockchainProperties config = sui();
        config.getSui().setChainIdentifier("aabbccdd");
        config.getSui().setPackageId(PACKAGE);
        config.getSui().setRegistryId(REGISTRY);
        AnchorCoordinates actual = AnchorCoordinates.from(null, null, config);
        assertThat(actual.chainId()).isZero();
        assertThat(actual.contractAddress()).isNull();
        assertThat(actual.transactionHash()).isNull();
        assertThat(actual.blockNumber()).isNull();
        assertThat(actual.metadata().provider()).isEqualTo("SUI");
        assertThat(actual.metadata().network()).isEqualTo("testnet");
        assertThat(actual.metadata().chainIdentifier()).isNull();
        assertThat(actual.metadata().packageId()).isNull();
        assertThat(actual.metadata().registryObjectId()).isNull();
        assertThat(actual.metadata().transactionDigest()).isNull();
        assertThat(actual.metadata().checkpointSequenceNumber()).isNull();
        assertThat(actual.metadata().checkpointDigest()).isNull();
        assertThat(actual.metadata().explorerUrl()).isNull();
        assertThat(actual.metadata().approvalScheme()).isNull();
    }

    @Test
    void legacyBatchWithoutContextIsNeverReclassifiedAsSuiByCurrentConfiguration() {
        AnchorCoordinates actual = AnchorCoordinates.from(mock(AncBatch.class), null, sui());
        assertThat(actual.metadata().provider()).isEqualTo("KAIA");
        assertThat(actual.metadata().approvalScheme()).isEqualTo("EIP712_V1");
        assertThat(actual.metadata().network()).isNull();
        assertThat(actual.metadata().packageId()).isNull();
        assertThat(actual.contractAddress()).isNull();
    }

    @Test
    void legacyTransactionWithoutContextKeepsItsOwnKaiaCoordinatesAfterSuiConfigurationChange() {
        AncChainTransaction tx = mock(AncChainTransaction.class);
        byte[] address = new byte[20]; java.util.Arrays.fill(address, (byte) 0x33);
        byte[] hash = new byte[32]; hash[31] = 7;
        when(tx.getChainId()).thenReturn(1001L);
        when(tx.getOperationType()).thenReturn(com.api.trekkey.domain.credential.entity.ChainOperationType.ANCHOR_BATCH);
        when(tx.getContractVersion()).thenReturn("1");
        when(tx.getContractAddress()).thenReturn(address);
        when(tx.getTxHash()).thenReturn(hash);
        when(tx.getBlockNumber()).thenReturn(123L);
        AnchorCoordinates actual = AnchorCoordinates.from(null, tx, sui());
        assertThat(actual.chainId()).isEqualTo(1001);
        assertThat(actual.contractAddress()).isEqualTo(KAIA_CONTRACT);
        assertThat(actual.transactionHash()).isEqualTo(Hash32.of(hash).hex());
        assertThat(actual.blockNumber()).isEqualTo(123);
        assertThat(actual.metadata().provider()).isEqualTo("KAIA");
        assertThat(actual.metadata().network()).isEqualTo("kairos");
        assertThat(actual.metadata().approvalScheme()).isEqualTo("EIP712_V1");
    }

    @Test
    void persistedSuiTransactionStillUsesStoredIdentityAndDigestsInsteadOfCurrentConfiguration() {
        AncChainTransaction tx = mock(AncChainTransaction.class);
        byte[] digest = new byte[32]; digest[31] = 8;
        byte[] checkpointDigest = new byte[32]; checkpointDigest[31] = 9;
        when(tx.getChainContext()).thenReturn("SUI|testnet|aabbccdd|" + PACKAGE + "|" + REGISTRY + "|1");
        when(tx.getOperationType()).thenReturn(com.api.trekkey.domain.credential.entity.ChainOperationType.ANCHOR_BATCH);
        when(tx.getContractAddress()).thenReturn(Hash32.fromHex(PACKAGE).bytes());
        when(tx.getContractVersion()).thenReturn("1");
        when(tx.getTxHash()).thenReturn(digest);
        when(tx.getBlockNumber()).thenReturn(321L);
        when(tx.getBlockHash()).thenReturn(checkpointDigest);
        BlockchainProperties differentConfig = new BlockchainProperties();
        differentConfig.setProvider(BlockchainProperties.Provider.KAIA);
        AnchorCoordinates actual = AnchorCoordinates.from(null, tx, differentConfig);
        assertThat(actual.chainId()).isZero();
        assertThat(actual.contractAddress()).isEqualTo(PACKAGE);
        assertThat(actual.transactionHash()).isEqualTo(SuiDigest.encode(digest));
        assertThat(actual.metadata().provider()).isEqualTo("SUI");
        assertThat(actual.metadata().chainIdentifier()).isEqualTo("aabbccdd");
        assertThat(actual.metadata().packageId()).isEqualTo(PACKAGE);
        assertThat(actual.metadata().registryObjectId()).isEqualTo(REGISTRY);
        assertThat(actual.metadata().checkpointSequenceNumber()).isEqualTo(321);
        assertThat(actual.metadata().checkpointDigest()).isEqualTo(SuiDigest.encode(checkpointDigest));
        assertThat(actual.metadata().approvalScheme()).isEqualTo("TREKKEY_SUI_APPROVAL_V1");
    }

    @Test
    void unbatchedKaiaPendingRetainsExistingLegacyResponseContract() {
        BlockchainProperties config = new BlockchainProperties();
        config.setProvider(BlockchainProperties.Provider.KAIA);
        config.setChainId(1001L);
        config.setContractAddress(KAIA_CONTRACT);
        AnchorCoordinates actual = AnchorCoordinates.from(null, null, config);
        assertThat(actual.chainId()).isEqualTo(1001);
        assertThat(actual.contractAddress()).isEqualTo(KAIA_CONTRACT);
        assertThat(actual.metadata().provider()).isEqualTo("KAIA");
        assertThat(actual.metadata().network()).isEqualTo("kairos");
        assertThat(actual.metadata().approvalScheme()).isEqualTo("EIP712_V1");
    }

    private BlockchainProperties sui() {
        BlockchainProperties config = new BlockchainProperties();
        config.setProvider(BlockchainProperties.Provider.SUI);
        config.getSui().setNetwork("testnet");
        return config;
    }
}
