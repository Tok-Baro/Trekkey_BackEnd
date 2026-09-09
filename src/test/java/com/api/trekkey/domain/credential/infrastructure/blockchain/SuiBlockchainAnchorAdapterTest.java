package com.api.trekkey.domain.credential.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.*;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.*;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.web3j.crypto.ECKeyPair;

class SuiBlockchainAnchorAdapterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PACKAGE = "0x" + "11".repeat(32), REGISTRY = "0x" + "22".repeat(32);
    private static final Hash32 TX = Hash32.fromHex("0x" + "33".repeat(32));
    private HttpServer server;
    private BlockchainProperties properties;
    private SuiBlockchainAnchorAdapter adapter;
    private ObjectNode identity, data, error;
    private int responseStatus;
    private final AtomicInteger operations = new AtomicInteger(), broadcasts = new AtomicInteger();

    @BeforeEach void setup() throws Exception {
        properties = new BlockchainProperties();
        properties.setProvider(BlockchainProperties.Provider.SUI);
        properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
        properties.getSui().setPackageId(PACKAGE);
        properties.getSui().setRegistryId(REGISTRY);
        properties.getSui().setChainIdentifier("aabbccdd");
        properties.getSui().setGatewayToken("synthetic-gateway-test-token-32-chars");
        identity = MAPPER.createObjectNode().put("protocolVersion", 1).put("network", "testnet")
                .put("chainIdentifier", "aabbccdd").put("packageId", PACKAGE).put("registryId", REGISTRY);
        data = MAPPER.createObjectNode();
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            boolean identityRequest = exchange.getRequestURI().getPath().equals("/v1/identity");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo("Bearer synthetic-gateway-test-token-32-chars");
            if (!identityRequest) {
                operations.incrementAndGet();
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                if (exchange.getRequestURI().getPath().equals("/v1/broadcast")) {
                    broadcasts.incrementAndGet();
                    var request = MAPPER.readTree(exchange.getRequestBody());
                    assertThat(request.path("transactionHash").asText()).isEqualTo(TX.hex());
                    assertThat(Base64.getDecoder().decode(request.path("signedRawTransaction").asText())).containsExactly(1, 2, 3);
                }
            }
            ObjectNode result = MAPPER.createObjectNode();
            if (!identityRequest && error != null) result.put("ok", false).set("error", error);
            else result.put("ok", true).set("data", identityRequest ? identity : data);
            byte[] bytes = MAPPER.writeValueAsBytes(result);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(identityRequest ? 200 : responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        properties.getSui().setGatewayUrl("http://127.0.0.1:" + server.getAddress().getPort());
        adapter = new SuiBlockchainAnchorAdapter(properties, MAPPER);
    }

    @AfterEach void close() { server.stop(0); }

    @Test void refusesIdentityMismatchBeforeIssuingAnOperation() {
        identity.put("registryId", "0x" + "44".repeat(32));
        assertThatThrownBy(() -> adapter.getBatch(TX)).isInstanceOfSatisfying(BlockchainGatewayException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo("SUI_IDENTITY_MISMATCH"));
        assertThat(operations).hasValue(0);
    }

    @Test void readsBatchAndRejectsMissingOrUnknownFieldsInsteadOfDefaultSuccess() {
        data.put("exists", true).put("issuerId", TX.hex()).put("merkleRoot", PACKAGE).put("schemaVersionHash", REGISTRY)
                .put("leafCount", "3").put("treeVersion", "1").put("issuerKeyVersion", "1").put("anchoredAt", "100");
        assertThat(adapter.getBatch(TX).leafCount()).isEqualTo(3);
        data.remove("anchoredAt");
        assertThatThrownBy(() -> adapter.getBatch(TX)).isInstanceOf(BlockchainGatewayException.class);
        data.removeAll().put("exists", false);
        assertThat(adapter.getBatch(TX).exists()).isFalse();
    }

    @Test void preparationReturnsNativeAddressAndNoNonceWithoutBroadcasting() {
        data.put("transactionHash", TX.hex()).put("transactionDigest", SuiDigest.encode(TX.bytes()))
                .putNull("transactionNonce").put("relayerAddress", PACKAGE)
                .put("signedRawTransaction", Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        var approval = new Eip712.BatchApproval(TX, TX, TX, TX, BigInteger.ONE, BigInteger.ONE,
                BigInteger.ONE, BigInteger.ONE, BigInteger.valueOf(100));
        var signature = Eip712.signDigest(TX, ECKeyPair.create(BigInteger.ONE));
        var prepared = adapter.prepareBatch(approval, signature);
        assertThat(prepared.transactionNonce()).isNull();
        assertThat(prepared.relayerAddress().bytes()).hasSize(32);
        assertThat(prepared.transactionHash()).isEqualTo(TX);
        assertThat(broadcasts).hasValue(0);
        data.put("transactionNonce", "0");
        assertThatThrownBy(() -> adapter.prepareBatch(approval, signature)).isInstanceOf(BlockchainGatewayException.class);
    }

    @Test void ambiguousBroadcastRequiresSamePersistedEnvelope() {
        error = MAPPER.createObjectNode().put("code", "BLOCKCHAIN_BROADCAST_AMBIGUOUS").put("retryable", true);
        responseStatus = 502;
        var prepared = new BlockchainAnchorPort.PreparedTransaction(TX, null, ChainAddress.of(Hash32.fromHex(PACKAGE).bytes()), new byte[] {1, 2, 3});
        assertThatThrownBy(() -> adapter.broadcast(prepared)).isInstanceOfSatisfying(BlockchainGatewayException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo("BLOCKCHAIN_BROADCAST_AMBIGUOUS");
            assertThat(failure.isRetryable()).isTrue();
        });
        assertThat(broadcasts).hasValue(1);
    }

    @Test void permanentEvidenceErrorIsNotConvertedToRetryByItsHttpStatus() {
        error = MAPPER.createObjectNode().put("code", "BLOCKCHAIN_RECEIPT_EVIDENCE_MISSING").put("retryable", false);
        responseStatus = 502;
        assertThatThrownBy(() -> adapter.getReceipt(TX, ChainOperationType.ANCHOR_BATCH))
                .isInstanceOfSatisfying(BlockchainGatewayException.class, failure -> assertThat(failure.isRetryable()).isFalse());
    }

    @Test void receiptRequiresMatchingNativeDigestCheckpointAndExactEventPosition() {
        data.put("state", "CONFIRMED").put("transactionDigest", SuiDigest.encode(TX.bytes()))
                .put("blockNumber", "412").put("blockHash", REGISTRY)
                .put("checkpointDigest", SuiDigest.encode(Hash32.fromHex(REGISTRY).bytes())).put("eventLogIndex", 2);
        var receipt = adapter.getReceipt(TX, ChainOperationType.ANCHOR_BATCH);
        assertThat(receipt.blockNumber()).isEqualTo(412);
        assertThat(receipt.eventLogIndex()).isEqualTo(2);
        data.put("checkpointDigest", SuiDigest.encode(TX.bytes()));
        assertThatThrownBy(() -> adapter.getReceipt(TX, ChainOperationType.ANCHOR_BATCH)).isInstanceOf(BlockchainGatewayException.class);
    }

    @Test void disabledAdapterDoesNotContactGateway() {
        properties.setMode(BlockchainProperties.Mode.DISABLED);
        assertThatThrownBy(() -> adapter.getBatch(TX)).isInstanceOf(BlockchainGatewayException.class);
        assertThat(operations).hasValue(0);
    }

    @Test void malformedSignerAndNativeDigestsAreNormalizedToNonRetryableGatewayErrors() {
        data.put("exists", true).put("signer", "0x1").put("validFrom", "1").put("validUntil", "0").put("compromisedAt", "0");
        assertThatThrownBy(() -> adapter.getIssuerKey(TX, 1)).isInstanceOfSatisfying(BlockchainGatewayException.class,
                failure -> { assertThat(failure.getErrorCode()).isEqualTo("SUI_RESPONSE_INVALID"); assertThat(failure.isRetryable()).isFalse(); });
        data.removeAll().put("state", "PENDING").put("transactionDigest", "not-base58");
        assertThatThrownBy(() -> adapter.getReceipt(TX, ChainOperationType.REVOKE)).isInstanceOfSatisfying(BlockchainGatewayException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("SUI_RESPONSE_INVALID"));
    }
}
