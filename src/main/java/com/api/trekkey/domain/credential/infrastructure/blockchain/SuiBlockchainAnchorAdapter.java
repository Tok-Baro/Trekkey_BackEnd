package com.api.trekkey.domain.credential.infrastructure.blockchain;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.*;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import com.api.trekkey.domain.credential.service.support.ApprovalPayloadFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Java ledger -> authenticated, versioned local Sui SDK gateway. No EVM RPC or wallet keys.
 * Preparation signs but NEVER broadcasts; the outbox persists the exact envelope first.
 */
@Component
@ConditionalOnProperty(prefix = "blockchain.anchoring", name = "provider", havingValue = "SUI")
public class SuiBlockchainAnchorAdapter implements BlockchainAnchorPort {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_RAW_BYTES = 65535;
    private final BlockchainProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public SuiBlockchainAnchorAdapter(BlockchainProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(properties.getSui().getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public OnChainIssuerKey getIssuerKey(Hash32 issuerId, long keyVersion) {
        if (issuerId == null || keyVersion < 1) throw invalid();
        JsonNode data = call("/v1/issuer-key", Map.of("issuerId", issuerId.hex(), "keyVersion", Long.toString(keyVersion)));
        boolean exists = bool(data, "exists");
        if (!exists) return new OnChainIssuerKey(EthereumAddress.fromBytes(new byte[20]), 0, 0, 0, false);
        EthereumAddress signer;
        try { signer = EthereumAddress.fromHex(text(data, "signer")); }
        catch (IllegalArgumentException exception) { throw invalid(); }
        return new OnChainIssuerKey(signer,
                number(data, "validFrom"), number(data, "validUntil"), number(data, "compromisedAt"), true);
    }

    @Override public OnChainBatch getBatch(Hash32 batchIdHash) {
        if (batchIdHash == null) throw invalid();
        JsonNode data = call("/v1/batch", Map.of("batchIdHash", batchIdHash.hex()));
        if (!bool(data, "exists")) return new OnChainBatch(Hash32.ZERO, Hash32.ZERO, Hash32.ZERO, 0, 0, 0, 0, false);
        long count = number(data, "leafCount");
        long version = number(data, "treeVersion");
        long keyVersion = number(data, "issuerKeyVersion");
        long time = number(data, "anchoredAt");
        if (count == 0 || count > 0xffffffffL || version == 0 || version > 65535 || keyVersion == 0 || time == 0) throw invalid();
        return new OnChainBatch(hash(data, "issuerId"), hash(data, "merkleRoot"), hash(data, "schemaVersionHash"),
                count, (int) version, keyVersion, time, true);
    }

    @Override public OnChainCredentialStatus getCredentialStatus(Hash32 issuerId, Hash32 credentialIdHash) {
        if (issuerId == null || credentialIdHash == null) throw invalid();
        JsonNode data = call("/v1/status", Map.of("issuerId", issuerId.hex(), "credentialIdHash", credentialIdHash.hex()));
        CredentialChainState state;
        try { state = CredentialChainState.valueOf(text(data, "state")); }
        catch (IllegalArgumentException exception) { throw invalid(); }
        long effective = number(data, "effectiveAt");
        long recorded = number(data, "recordedAt");
        long keyVersion = number(data, "issuerKeyVersion");
        Hash32 replacement = hash(data, "replacementCredentialIdHash");
        if (state == CredentialChainState.NONE) {
            if (effective != 0 || recorded != 0 || keyVersion != 0 || !replacement.isZero()) throw invalid();
        } else if (effective == 0 || recorded < effective || keyVersion == 0
                || (state == CredentialChainState.REVOKED && !replacement.isZero())
                || (state == CredentialChainState.SUPERSEDED && replacement.isZero())) throw invalid();
        return new OnChainCredentialStatus(state, effective, recorded, keyVersion, replacement);
    }

    @Override public PreparedTransaction prepareBatch(Eip712.BatchApproval approval, Signature65 signature) {
        requireWrite();
        return prepare("/v1/prepare-batch", ApprovalPayloadFactory.batchPayload(properties, approval), signature);
    }

    @Override public PreparedTransaction prepareStatus(Eip712.StatusApproval approval, Signature65 signature) {
        requireWrite();
        return prepare("/v1/prepare-status", ApprovalPayloadFactory.statusPayload(properties, approval), signature);
    }

    private PreparedTransaction prepare(String path, String payload, Signature65 signature) {
        if (signature == null) throw invalid();
        try {
            JsonNode data = call(path, Map.of("approval", mapper.readTree(payload).get("message"), "issuerSignature", signature.hex()));
            Hash32 transactionHash = hash(data, "transactionHash");
            if (!SuiDigest.decode(text(data, "transactionDigest")).equals(transactionHash)) throw invalid();
            if (!data.has("transactionNonce") || !data.get("transactionNonce").isNull()) throw invalid();
            byte[] sender = hash(data, "relayerAddress").bytes();
            byte[] raw = Base64.getDecoder().decode(text(data, "signedRawTransaction"));
            if (raw.length == 0 || raw.length > MAX_RAW_BYTES) throw invalid();
            return new PreparedTransaction(transactionHash, null, ChainAddress.of(sender), raw);
        } catch (IOException | IllegalArgumentException exception) { throw invalid(); }
    }

    @Override public void broadcast(PreparedTransaction transaction) {
        requireWrite();
        if (transaction == null || transaction.transactionNonce() != null || transaction.relayerAddress().bytes().length != 32
                || transaction.signedRawTransaction().length > MAX_RAW_BYTES) throw invalid();
        // A retryable transport failure AFTER a request may have executed. Persist UNKNOWN and reuse the same bytes.
        try {
            JsonNode result = call("/v1/broadcast", Map.of("transactionHash", transaction.transactionHash().hex(),
                    "signedRawTransaction", Base64.getEncoder().encodeToString(transaction.signedRawTransaction())));
            if (!transaction.transactionHash().equals(hash(result, "transactionHash"))) throw invalid();
        } catch (BlockchainGatewayException exception) {
            if (exception.isRetryable() || "SUI_RESPONSE_INVALID".equals(exception.getErrorCode())) {
                throw new BlockchainGatewayException("BLOCKCHAIN_BROADCAST_AMBIGUOUS", true, "Sui submission outcome is unknown");
            }
            throw exception;
        }
    }

    @Override public ChainReceipt getReceipt(Hash32 transactionHash, ChainOperationType operationType) {
        if (transactionHash == null || operationType == null) throw invalid();
        JsonNode data = call("/v1/receipt", Map.of("transactionHash", transactionHash.hex(), "operationType", operationType.name()));
        if (!nativeDigest(data, "transactionDigest").equals(transactionHash)) throw invalid();
        String state = text(data, "state");
        if (state.equals("PENDING")) return ChainReceipt.pending();
        if (state.equals("REVERTED")) return new ChainReceipt(ReceiptState.REVERTED, 0, null, -1, "SUI_EXECUTION_FAILED");
        if (!state.equals("CONFIRMED")) throw invalid();
        if (!nativeDigest(data, "checkpointDigest").equals(hash(data, "blockHash"))) throw invalid();
        long event = number(data, "eventLogIndex");
        if (event > Integer.MAX_VALUE) throw invalid();
        // Legacy port labels blockNumber/blockHash denote real checkpoint coordinates on Sui.
        return new ChainReceipt(ReceiptState.CONFIRMED, number(data, "blockNumber"), hash(data, "blockHash"), (int) event, null);
    }

    private JsonNode call(String path, Object request) {
        if (!properties.isSui() || !properties.isReadEnabled()) {
            throw new BlockchainGatewayException("BLOCKCHAIN_READ_DISABLED", false, "Sui reads are disabled");
        }
        JsonNode identity = exchange("/v1/identity", null);
        var expected = properties.getSui();
        if (number(identity, "protocolVersion") != 1 || !text(identity, "network").equals(expected.getNetwork())
                || !text(identity, "chainIdentifier").equalsIgnoreCase(expected.getChainIdentifier())
                || !text(identity, "packageId").equalsIgnoreCase(expected.getPackageId())
                || !text(identity, "registryId").equalsIgnoreCase(expected.getRegistryId())) {
            throw new BlockchainGatewayException("SUI_IDENTITY_MISMATCH", false, "Sui gateway/registry network identity does not match configuration");
        }
        return exchange(path, request);
    }

    private JsonNode exchange(String path, Object request) {
        try {
            String origin = properties.getSui().getGatewayUrl().replaceAll("/+$", "");
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + path))
                    .timeout(properties.getSui().getRequestTimeout())
                    .header("Authorization", "Bearer " + properties.getSui().getGatewayToken())
                    .header("Accept", "application/json");
            if (request == null) builder.GET();
            else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(request)));
            HttpResponse<java.io.InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var body = response.body()) { bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1); }
            if (bytes.length > MAX_RESPONSE_BYTES) throw invalid();
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new BlockchainGatewayException("SUI_GATEWAY_UNAUTHORIZED", false, "Sui gateway authentication failed");
            }
            JsonNode envelope;
            try { envelope = mapper.readTree(bytes); }
            catch (IOException exception) {
                if (response.statusCode() >= 500 || response.statusCode() == 429) {
                    throw new BlockchainGatewayException("SUI_GATEWAY_UNAVAILABLE", true, "Sui gateway temporarily unavailable");
                }
                throw invalid();
            }
            if (envelope == null || !envelope.isObject() || !envelope.path("ok").isBoolean()) throw invalid();
            if (!envelope.get("ok").booleanValue()) {
                JsonNode error = envelope.path("error");
                String code = text(error, "code");
                if (!code.matches("[A-Z0-9_]{1,80}")) throw invalid();
                throw new BlockchainGatewayException(code, bool(error, "retryable"), "Sui gateway rejected the operation");
            }
            if (response.statusCode() >= 500 || response.statusCode() == 429) {
                throw new BlockchainGatewayException("SUI_GATEWAY_UNAVAILABLE", true, "Sui gateway temporarily unavailable");
            }
            if (response.statusCode() != 200 || !envelope.path("data").isObject()) throw invalid();
            return envelope.get("data");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BlockchainGatewayException("SUI_GATEWAY_INTERRUPTED", true, "Sui gateway request interrupted");
        } catch (IOException exception) {
            throw new BlockchainGatewayException("SUI_GATEWAY_UNAVAILABLE", true, "Sui gateway request failed");
        } catch (IllegalArgumentException exception) { throw invalid(); }
    }

    private void requireWrite() {
        if (!properties.isWriteEnabled() || "mainnet".equals(properties.getSui().getNetwork())) {
            throw new BlockchainGatewayException("BLOCKCHAIN_WRITE_DISABLED", false, "Local Sui writes are disabled");
        }
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid();
        return value.textValue();
    }
    private static boolean bool(JsonNode node, String field) {
        if (!node.path(field).isBoolean()) throw invalid();
        return node.get(field).booleanValue();
    }
    private static long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        String encoded = value.asText();
        if ((!value.isTextual() && !value.isIntegralNumber()) || !encoded.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(encoded); } catch (NumberFormatException exception) { throw invalid(); }
    }
    private static Hash32 hash(JsonNode node, String field) {
        try { return Hash32.fromHex(text(node, field)); } catch (IllegalArgumentException exception) { throw invalid(); }
    }
    private static Hash32 nativeDigest(JsonNode node, String field) {
        try { return SuiDigest.decode(text(node, field)); } catch (IllegalArgumentException exception) { throw invalid(); }
    }
    private static BlockchainGatewayException invalid() {
        return new BlockchainGatewayException("SUI_RESPONSE_INVALID", false, "Sui gateway returned an invalid or inconsistent response");
    }
}
