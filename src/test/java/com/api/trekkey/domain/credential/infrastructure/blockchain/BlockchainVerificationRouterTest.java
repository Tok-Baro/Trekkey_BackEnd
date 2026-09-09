package com.api.trekkey.domain.credential.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.entity.*;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.EthGetCode;

class BlockchainVerificationRouterTest {
    private static final String CONTRACT = "0x" + "11".repeat(20);
    private static final String PACKAGE = "0x" + "22".repeat(32);
    private final BlockchainAnchorPort active = mock(BlockchainAnchorPort.class);
    private final BlockchainAnchorPort legacy = mock(BlockchainAnchorPort.class);

    @Test void legacyNullContextsRouteFromStoredTransactionAndNeverTouchTheActiveSuiAdapter() {
        BlockchainProperties properties = suiWithLegacy();
        AtomicReference<BlockchainProperties> readerConfig = new AtomicReference<>();
        var router = new BlockchainVerificationRouter(properties, active, config -> {
            readerConfig.set(config); return legacy;
        });
        AncBatch batch = batch(null);
        AncChainTransaction tx = legacyTransaction();
        byte[] canonicalEnvelope = tx.getSignedRawTransaction();
        var selected = router.resolve(batch, tx);
        selected.reader().getBatch(Hash32.ZERO);
        selected.reader().getIssuerKey(Hash32.ZERO, 1);
        selected.reader().getCredentialStatus(Hash32.ZERO, Hash32.ZERO);
        verify(legacy).getBatch(Hash32.ZERO);
        verify(legacy).getIssuerKey(Hash32.ZERO, 1);
        verify(legacy).getCredentialStatus(Hash32.ZERO, Hash32.ZERO);
        verifyNoMoreInteractions(legacy); verifyNoInteractions(active);
        assertThat(selected.identity().context()).isEqualTo("KAIA|1001|" + CONTRACT + "|1");
        assertThat(selected.identity().matchesIssuerContext(null)).isTrue();
        assertThat(selected.reader()).isNotInstanceOf(BlockchainAnchorPort.class);
        assertThat(readerConfig.get().getMode()).isEqualTo(BlockchainProperties.Mode.READ_ONLY);
        assertThat(readerConfig.get().getRelayerPrivateKey()).isNull();
        assertThat(readerConfig.get().isWorkerEnabled()).isFalse();
        assertThat(tx.getChainContext()).isNull();
        assertThat(tx.getSignedRawTransaction()).isEqualTo(canonicalEnvelope);
        assertThat(batch.getChainContext()).isNull();
    }

    @Test void explicitSuiEvidenceUsesOnlyTheActiveReader() {
        BlockchainProperties properties = suiWithLegacy();
        var router = router(properties);
        AncBatch batch = batch(properties.chainContext());
        AncChainTransaction tx = AncChainTransaction.pending(7L, null, null, ChainOperationType.ANCHOR_BATCH,
                "sui", 0, Hash32.fromHex(PACKAGE).bytes(), "1", LocalDateTime.now()).inContext(properties.chainContext());
        var route = router.resolve(batch, tx);
        route.reader().getBatch(Hash32.ZERO);
        verify(active).getBatch(Hash32.ZERO); verifyNoInteractions(legacy);
        assertThat(route.identity().matchesIssuerContext(null)).isFalse();
        assertThat(route.identity().matchesIssuerContext(properties.chainContext())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BATCH", "TRANSACTION", "BOTH"})
    void migrationContextBackfillKeepsLegacyReadsAndNullIssuerKeyOnKaiaWithSuiWritesEnabled(String backfill) {
        BlockchainProperties properties = suiWithLegacy();
        properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
        String historicalContext = "KAIA|1001|" + CONTRACT + "|1";
        AncBatch batch = batch(backfill.equals("TRANSACTION") ? null : historicalContext);
        AncChainTransaction tx = legacyTransaction();
        if (!backfill.equals("BATCH")) ReflectionTestUtils.setField(tx, "chainContext", historicalContext);
        byte[] signedBytes = tx.getSignedRawTransaction();
        byte[] oldContract = tx.getContractAddress();

        var selected = router(properties).resolve(batch, tx);
        selected.reader().getBatch(Hash32.ZERO);
        selected.reader().getIssuerKey(Hash32.ZERO, 1);
        selected.reader().getCredentialStatus(Hash32.ZERO, Hash32.ZERO);

        assertThat(selected.identity().context()).isEqualTo(historicalContext);
        assertThat(selected.identity().matchesIssuerContext(null)).isTrue();
        assertThat(selected.reader()).isNotInstanceOf(BlockchainAnchorPort.class);
        assertThat(tx.getContractAddress()).hasSize(20).isEqualTo(oldContract);
        assertThat(tx.getSignedRawTransaction()).isEqualTo(signedBytes);
        verify(legacy).getBatch(Hash32.ZERO);
        verify(legacy).getIssuerKey(Hash32.ZERO, 1);
        verify(legacy).getCredentialStatus(Hash32.ZERO, Hash32.ZERO);
        verifyNoMoreInteractions(legacy);
        verifyNoInteractions(active);
    }

    @Test void missingLegacyTransactionDoesNotInferTheCurrentOrOnlyAllowlistedDeployment() {
        reject(router(suiWithLegacy()), batch(null), null);
    }

    @Test void unknownContractChainOrVersionIsNeverSentToAnyRpc() {
        for (String field : List.of("contractAddress", "chainId", "contractVersion")) {
            AncChainTransaction tx = legacyTransaction();
            ReflectionTestUtils.setField(tx, field, switch (field) {
                case "chainId" -> 8217L;
                case "contractVersion" -> "2";
                default -> EthereumAddress.fromHex("0x" + "33".repeat(20)).bytes();
            });
            reject(router(suiWithLegacy()), batch(null), tx);
        }
    }

    @Test void explicitlyDisabledLegacyRouteDoesNotBecomeAFallback() {
        BlockchainProperties properties = suiWithLegacy();
        properties.getLegacyKaiaReadRoutes().getFirst().setEnabled(false);
        reject(router(properties), batch(null), legacyTransaction());
    }

    @Test void globallyDisabledReadsNeverUseEnabledHistoricalRoutes() {
        BlockchainProperties properties = suiWithLegacy();
        properties.setMode(BlockchainProperties.Mode.DISABLED);
        reject(router(properties), batch(null), legacyTransaction());
    }

    @Test void duplicateRoutesFailClosedEvenIfValidationWasBypassed() {
        BlockchainProperties properties = suiWithLegacy();
        properties.getLegacyKaiaReadRoutes().add(legacyRoute());
        reject(router(properties), batch(null), legacyTransaction());
    }

    @Test void batchAndTransactionContextDisagreementOrMalformedContextFailClosed() {
        for (String context : List.of("KAIA|1001|" + CONTRACT + "|2", "SUI|bogus", "UNKNOWN|1001", "KAIA|x|a|1")) {
            reject(router(suiWithLegacy()), batch(context), legacyTransaction());
        }
    }

    @Test void transactionCannotBorrowAnotherBatchOrSuiContextToRouteHistoricalBytes() {
        AncChainTransaction tx = legacyTransaction();
        ReflectionTestUtils.setField(tx, "batchId", 8L);
        reject(router(suiWithLegacy()), batch(null), tx);
        tx = legacyTransaction();
        ReflectionTestUtils.setField(tx, "chainContext", suiWithLegacy().chainContext());
        reject(router(suiWithLegacy()), batch(null), tx);
    }

    @Test void aSuiTransactionWithoutItsRegistrySnapshotIsNeverInferredFromActiveConfiguration() {
        BlockchainProperties properties = suiWithLegacy();
        AncChainTransaction tx = AncChainTransaction.pending(7L, null, null, ChainOperationType.ANCHOR_BATCH,
                "sui", 0, Hash32.fromHex(PACKAGE).bytes(), "1", LocalDateTime.now());
        reject(router(properties), batch(properties.chainContext()), tx);
    }

    @Test void rpcFailuresRemainFailuresRatherThanFallingBackToAnotherProvider() {
        when(legacy.getBatch(Hash32.ZERO)).thenThrow(new BlockchainGatewayException("BLOCKCHAIN_CHAIN_ID_MISMATCH", false, "wrong chain"));
        var selected = router(suiWithLegacy()).resolve(batch(null), legacyTransaction());
        assertThatThrownBy(() -> selected.reader().getBatch(Hash32.ZERO)).isInstanceOf(BlockchainGatewayException.class);
        verifyNoInteractions(active);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WRONG_CHAIN", "WRONG_CODE", "NO_CODE", "UNAVAILABLE"})
    @SuppressWarnings("unchecked")
    void actualHistoricalAdapterRejectsUnverifiedRpcIdentityBeforeReadingAnyClaims(String failure) throws Exception {
        Web3j rpc = mock(Web3j.class);
        Request<?, EthChainId> chainRequest = mock(Request.class);
        doReturn(chainRequest).when(rpc).ethChainId();
        if (failure.equals("UNAVAILABLE")) {
            when(chainRequest.send()).thenThrow(new IOException("synthetic outage"));
        } else {
            EthChainId chainId = new EthChainId(); chainId.setResult(failure.equals("WRONG_CHAIN") ? "0x2019" : "0x3e9");
            when(chainRequest.send()).thenReturn(chainId);
        }
        if (failure.equals("WRONG_CODE") || failure.equals("NO_CODE")) {
            Request<?, EthGetCode> codeRequest = mock(Request.class);
            doReturn(codeRequest).when(rpc).ethGetCode(CONTRACT, DefaultBlockParameterName.LATEST);
            EthGetCode code = new EthGetCode(); code.setResult(failure.equals("NO_CODE") ? "0x" : "0x6000");
            when(codeRequest.send()).thenReturn(code);
        }
        var router = new BlockchainVerificationRouter(suiWithLegacy(), active,
                config -> new Web3jKaiaBlockchainAnchorAdapter(config, () -> rpc));
        try {
            var selected = router.resolve(batch(null), legacyTransaction());
            assertThatThrownBy(() -> selected.reader().getBatch(Hash32.ZERO))
                    .isInstanceOf(BlockchainGatewayException.class)
                    .satisfies(error -> {
                        var gateway = (BlockchainGatewayException) error;
                        assertThat(gateway.isRetryable()).isEqualTo(failure.equals("UNAVAILABLE"));
                        assertThat(gateway.getErrorCode()).isEqualTo(switch (failure) {
                            case "WRONG_CHAIN" -> "BLOCKCHAIN_CHAIN_ID_MISMATCH";
                            case "WRONG_CODE" -> "BLOCKCHAIN_CONTRACT_CODE_MISMATCH";
                            case "NO_CODE" -> "BLOCKCHAIN_CONTRACT_CODE_MISSING";
                            default -> "BLOCKCHAIN_RPC_UNAVAILABLE";
                        });
                    });
            verify(rpc, never()).ethCall(any(), any());
            verifyNoInteractions(active);
        } finally { router.closeReaders(); }
    }

    private BlockchainVerificationRouter router(BlockchainProperties properties) {
        return new BlockchainVerificationRouter(properties, active, ignored -> legacy);
    }
    private void reject(BlockchainVerificationRouter router, AncBatch batch, AncChainTransaction tx) {
        assertThatThrownBy(() -> router.resolve(batch, tx)).isInstanceOf(BlockchainGatewayException.class)
                .satisfies(error -> assertThat(((BlockchainGatewayException) error).isRetryable()).isFalse());
        verifyNoInteractions(active, legacy);
    }
    static BlockchainProperties.LegacyKaiaReadRoute legacyRoute() {
        var route = new BlockchainProperties.LegacyKaiaReadRoute();
        route.setEnabled(true); route.setChainId(1001); route.setContractAddress(CONTRACT);
        route.setContractVersion("1"); route.setRuntimeCodeHash("0x" + "44".repeat(32));
        route.setRpcUrl("https://kairos.example.invalid"); return route;
    }
    private static BlockchainProperties suiWithLegacy() {
        var properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.READ_ONLY); properties.setProvider(BlockchainProperties.Provider.SUI);
        properties.getSui().setChainIdentifier("aabbccdd"); properties.getSui().setPackageId(PACKAGE);
        properties.getSui().setRegistryId("0x" + "33".repeat(32));
        properties.getLegacyKaiaReadRoutes().add(legacyRoute()); return properties;
    }
    private static AncBatch batch(String context) {
        AncBatch batch = AncBatch.seal(1L, 2L, "batch", new byte[32], new byte[32], 1, 1, new byte[32],
                1L, LocalDateTime.now().plusMinutes(15), LocalDateTime.now());
        if (context != null) batch.inContext(context);
        ReflectionTestUtils.setField(batch, "id", 7L); return batch;
    }
    private static AncChainTransaction legacyTransaction() {
        AncChainTransaction tx = AncChainTransaction.pending(7L, null, null, ChainOperationType.ANCHOR_BATCH,
                "legacy", 1001, EthereumAddress.fromHex(CONTRACT).bytes(), "1", LocalDateTime.now());
        tx.prepare(new byte[]{1, 2, 3}, new byte[32], 2L, new byte[20], LocalDateTime.now()); return tx;
    }
}
