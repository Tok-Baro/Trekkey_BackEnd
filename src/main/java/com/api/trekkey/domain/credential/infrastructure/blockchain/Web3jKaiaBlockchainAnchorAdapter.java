package com.api.trekkey.domain.credential.infrastructure.blockchain;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Signature65;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/**
 * Bounded standard EVM JSON-RPC adapter. It deliberately knows no Kaia-specific SDK types.
 */
@Component
public class Web3jKaiaBlockchainAnchorAdapter implements BlockchainAnchorPort {

    private static final BigInteger GAS_MARGIN_NUMERATOR = BigInteger.valueOf(120);
    private static final BigInteger GAS_MARGIN_DENOMINATOR = BigInteger.valueOf(100);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private final BlockchainProperties properties;
    private final Supplier<Web3j> web3jFactory;
    private final Object web3jMonitor = new Object();

    private volatile Web3j web3j;
    private volatile boolean chainIdVerified;

    @Autowired
    public Web3jKaiaBlockchainAnchorAdapter(BlockchainProperties properties) {
        this(properties, null);
    }

    Web3jKaiaBlockchainAnchorAdapter(BlockchainProperties properties, Supplier<Web3j> web3jFactory) {
        this.properties = properties;
        this.web3jFactory = web3jFactory;
    }

    @Override
    public OnChainIssuerKey getIssuerKey(Hash32 issuerId, long keyVersion) {
        if (issuerId == null || keyVersion < 0) {
            throw invalidResponse("issuerId and unsigned keyVersion are required");
        }
        Function function = TrekkeyRegistryAbi.getIssuerKey(issuerId, keyVersion);
        TrekkeyRegistryAbi.IssuerKeyTuple tuple = decodeSingle(call(function), function, TrekkeyRegistryAbi.IssuerKeyTuple.class);
        EthereumAddress signer = address(tuple.signer.getValue(), "issuer key signer");
        return new OnChainIssuerKey(
                signer,
                asLong(tuple.validFrom.getValue(), "issuer key validFrom"),
                asLong(tuple.validUntil.getValue(), "issuer key validUntil"),
                asLong(tuple.compromisedAt.getValue(), "issuer key compromisedAt"),
                !isZeroAddress(signer));
    }

    @Override
    public OnChainBatch getBatch(Hash32 batchIdHash) {
        if (batchIdHash == null) {
            throw invalidResponse("batchIdHash is required");
        }
        Function function = TrekkeyRegistryAbi.getBatch(batchIdHash);
        List<Type> values = decode(call(function), function);
        TrekkeyRegistryAbi.BatchReadResult decoded;
        try {
            decoded = TrekkeyRegistryAbi.decodeBatchResult(values);
        } catch (IllegalArgumentException exception) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_RESPONSE_INVALID", false, "getBatch returned an unexpected ABI shape", exception);
        }
        TrekkeyRegistryAbi.BatchTuple tuple = decoded.batch();
        return new OnChainBatch(
                hash(tuple.issuerId.getValue(), "batch issuerId"),
                hash(tuple.merkleRoot.getValue(), "batch merkleRoot"),
                hash(tuple.schemaVersionHash.getValue(), "batch schemaVersionHash"),
                asLong(tuple.leafCount.getValue(), "batch leafCount"),
                asInt(tuple.treeVersion.getValue(), "batch treeVersion"),
                asLong(tuple.issuerKeyVersion.getValue(), "batch issuerKeyVersion"),
                asLong(tuple.anchoredAt.getValue(), "batch anchoredAt"),
                decoded.exists());
    }

    @Override
    public OnChainCredentialStatus getCredentialStatus(Hash32 issuerId, Hash32 credentialIdHash) {
        if (issuerId == null || credentialIdHash == null) {
            throw invalidResponse("issuerId and credentialIdHash are required");
        }
        Function function = TrekkeyRegistryAbi.getCredentialStatus(issuerId, credentialIdHash);
        TrekkeyRegistryAbi.StatusTuple tuple = decodeSingle(call(function), function, TrekkeyRegistryAbi.StatusTuple.class);
        return new OnChainCredentialStatus(
                credentialState(tuple.state.getValue()),
                asLong(tuple.effectiveAt.getValue(), "credential effectiveAt"),
                asLong(tuple.recordedAt.getValue(), "credential recordedAt"),
                asLong(tuple.issuerKeyVersion.getValue(), "credential issuerKeyVersion"),
                hash(tuple.replacementCredentialIdHash.getValue(), "credential replacementCredentialIdHash"));
    }

    @Override
    public PreparedTransaction prepareBatch(Eip712.BatchApproval approval, Signature65 issuerSignature) {
        if (approval == null || issuerSignature == null) {
            throw invalidResponse("batch approval and issuer signature are required");
        }
        return prepare(TrekkeyRegistryAbi.anchorBatch(approval, issuerSignature));
    }

    @Override
    public PreparedTransaction prepareStatus(Eip712.StatusApproval approval, Signature65 issuerSignature) {
        if (approval == null || issuerSignature == null) {
            throw invalidResponse("status approval and issuer signature are required");
        }
        return switch (approval.action()) {
            case REVOKE -> prepare(TrekkeyRegistryAbi.revokeCredential(approval, issuerSignature));
            case SUPERSEDE -> prepare(TrekkeyRegistryAbi.supersedeCredential(approval, issuerSignature));
        };
    }

    @Override
    public void broadcast(PreparedTransaction prepared) {
        requireWriteAccess();
        if (prepared == null) {
            throw invalidResponse("prepared transaction is required");
        }
        byte[] raw = prepared.signedRawTransaction();
        Hash32 localHash = Hash32.of(Hash.sha3(raw));
        if (!localHash.equals(prepared.transactionHash())) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_PREPARED_HASH_MISMATCH",
                    false,
                    "prepared transaction hash does not match its signed raw transaction");
        }
        try {
            EthSendTransaction response = client().ethSendRawTransaction(Numeric.toHexString(raw)).send();
            if (response.hasError()) {
                String message = response.getError().getMessage();
                if (isAlreadyKnown(message)) {
                    return;
                }
                if (!isDeterministicBroadcastRejection(message)) {
                    throw new BlockchainGatewayException(
                            "BLOCKCHAIN_BROADCAST_AMBIGUOUS",
                            true,
                            "blockchain node returned an ambiguous broadcast error");
                }
                throw new BlockchainGatewayException(
                        "BLOCKCHAIN_BROADCAST_REJECTED",
                        false,
                        "blockchain node rejected the prepared transaction (code " + response.getError().getCode() + ")");
            }
            Hash32 nodeHash = hash(response.getTransactionHash(), "submitted transaction hash");
            if (!localHash.equals(nodeHash)) {
                throw new BlockchainGatewayException(
                        "BLOCKCHAIN_BROADCAST_AMBIGUOUS",
                        true,
                        "node returned an unexpected transaction hash; receipt reconciliation is required");
            }
        } catch (IOException exception) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_BROADCAST_AMBIGUOUS",
                    true,
                    "broadcast response was not received; receipt reconciliation is required",
                    exception);
        }
    }

    @Override
    public ChainReceipt getReceipt(Hash32 transactionHash, ChainOperationType operationType) {
        requireReadAccess();
        if (transactionHash == null || operationType == null) {
            throw invalidResponse("transactionHash and operationType are required");
        }
        Hash32 expectedTopic;
        try {
            expectedTopic = TrekkeyRegistryAbi.expectedEventTopic(operationType);
        } catch (IllegalArgumentException exception) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_OPERATION_UNSUPPORTED", false, exception.getMessage(), exception);
        }

        EthGetTransactionReceipt response = send(client().ethGetTransactionReceipt(transactionHash.hex()));
        Optional<TransactionReceipt> receipt = response.getTransactionReceipt();
        if (receipt.isEmpty()) {
            return ChainReceipt.pending();
        }

        TransactionReceipt value = receipt.get();
        ReceiptLocation location = receiptLocation(value);
        if (!value.isStatusOK()) {
            return new ChainReceipt(ReceiptState.REVERTED, location.blockNumber(), location.blockHash(), -1,
                    blankToNull(value.getRevertReason()));
        }

        int eventLogIndex = expectedEventLogIndex(value.getLogs(), expectedTopic, contractAddress());
        if (eventLogIndex < 0) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_RECEIPT_EVIDENCE_MISSING",
                    false,
                    "successful receipt did not contain the expected registry event");
        }
        return new ChainReceipt(ReceiptState.CONFIRMED, location.blockNumber(), location.blockHash(), eventLogIndex, null);
    }

    private PreparedTransaction prepare(Function function) {
        requireWriteAccess();
        Credentials credentials = relayerCredentials();
        EthereumAddress contract = contractAddress();
        String callData = TrekkeyRegistryAbi.encode(function);

        Web3j client = client();
        String relayerAddress = credentials.getAddress();
        BigInteger nonce = requireUnsigned(
                send(client.ethGetTransactionCount(relayerAddress, DefaultBlockParameterName.PENDING))
                        .getTransactionCount(),
                "pending relayer nonce");
        BigInteger gasPrice = requireUnsigned(send(client.ethGasPrice()).getGasPrice(), "gas price");
        BigInteger estimatedGas = requirePositive(
                send(client.ethEstimateGas(Transaction.createEthCallTransaction(relayerAddress, contract.hex(), callData)))
                        .getAmountUsed(),
                "estimated gas");
        BigInteger gasLimit = withGasMargin(estimatedGas);

        RawTransaction transaction = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, contract.hex(), BigInteger.ZERO, callData);
        byte[] signedTransaction = TransactionEncoder.signMessage(transaction, properties.getChainId(), credentials);
        Hash32 localTransactionHash = Hash32.of(Hash.sha3(signedTransaction));
        return new PreparedTransaction(
                localTransactionHash,
                asLong(nonce, "pending relayer nonce"),
                EthereumAddress.fromHex(relayerAddress),
                signedTransaction);
    }

    private String call(Function function) {
        requireReadAccess();
        EthCall response = send(client().ethCall(
                Transaction.createEthCallTransaction(null, contractAddress().hex(), TrekkeyRegistryAbi.encode(function)),
                DefaultBlockParameterName.LATEST));
        String value = response.getValue();
        if (value == null || !value.startsWith("0x")) {
            throw invalidResponse("eth_call returned an invalid result");
        }
        return value;
    }

    private List<Type> decode(String value, Function function) {
        try {
            return FunctionReturnDecoder.decode(value, function.getOutputParameters());
        } catch (RuntimeException exception) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_RESPONSE_INVALID", false, "registry response could not be ABI decoded", exception);
        }
    }

    private <T extends Type> T decodeSingle(String value, Function function, Class<T> expectedType) {
        List<Type> values = decode(value, function);
        if (values.size() != 1 || !expectedType.isInstance(values.get(0))) {
            throw invalidResponse("registry response did not contain the expected tuple");
        }
        return expectedType.cast(values.get(0));
    }

    private Web3j web3j() {
        Web3j current = web3j;
        if (current != null) {
            return current;
        }
        synchronized (web3jMonitor) {
            if (web3j == null) {
                web3j = web3jFactory != null
                        ? web3jFactory.get()
                        : Web3j.build(new HttpService(properties.getRpcUrl()));
                if (web3j == null) {
                    throw new BlockchainGatewayException(
                            "BLOCKCHAIN_CONFIGURATION_INVALID", false, "web3j factory returned null");
                }
            }
            return web3j;
        }
    }

    private Web3j client() {
        Web3j client = web3j();
        if (chainIdVerified) {
            return client;
        }
        synchronized (web3jMonitor) {
            if (!chainIdVerified) {
                BigInteger actualChainId = requireUnsigned(send(client.ethChainId()).getChainId(), "RPC chainId");
                if (!BigInteger.valueOf(properties.getChainId()).equals(actualChainId)) {
                    throw new BlockchainGatewayException(
                            "BLOCKCHAIN_CHAIN_ID_MISMATCH",
                            false,
                            "RPC chainId does not match blockchain.anchoring.chainId");
                }
                chainIdVerified = true;
            }
        }
        return client;
    }

    private void requireReadAccess() {
        if (!properties.isReadEnabled()) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_READ_DISABLED", false, "blockchain reads are disabled");
        }
        validateConnectionConfiguration();
    }

    private void requireWriteAccess() {
        if (!properties.isWriteEnabled()) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_WRITE_DISABLED", false, "blockchain writes require LOCAL_RELAYER mode");
        }
        validateConnectionConfiguration();
        relayerCredentials();
    }

    private void validateConnectionConfiguration() {
        if (properties.getChainId() <= 0) {
            throw configuration("chainId must be greater than zero");
        }
        try {
            URI rpcUri = new URI(properties.getRpcUrl());
            if ((!"http".equalsIgnoreCase(rpcUri.getScheme()) && !"https".equalsIgnoreCase(rpcUri.getScheme()))
                    || rpcUri.getHost() == null) {
                throw configuration("rpcUrl must be an absolute HTTP(S) URL");
            }
        } catch (URISyntaxException | NullPointerException exception) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_CONFIGURATION_INVALID", false, "rpcUrl must be an absolute HTTP(S) URL", exception);
        }
        contractAddress();
    }

    private EthereumAddress contractAddress() {
        try {
            EthereumAddress address = EthereumAddress.fromHex(properties.getContractAddress());
            if (isZeroAddress(address)) {
                throw configuration("contractAddress must not be the zero address");
            }
            return address;
        } catch (RuntimeException exception) {
            if (exception instanceof BlockchainGatewayException gatewayException) {
                throw gatewayException;
            }
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_CONFIGURATION_INVALID", false, "contractAddress must be a 20-byte hex address", exception);
        }
    }

    private Credentials relayerCredentials() {
        String configuredKey = properties.getRelayerPrivateKey();
        String rawKey = configuredKey == null ? null : Numeric.cleanHexPrefix(configuredKey);
        if (rawKey == null || !rawKey.matches("[0-9a-fA-F]{64}")) {
            throw configuration("relayerPrivateKey must be a 32-byte hex key");
        }
        try {
            return Credentials.create(rawKey);
        } catch (RuntimeException exception) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_CONFIGURATION_INVALID", false, "relayerPrivateKey is invalid", exception);
        }
    }

    private static BigInteger withGasMargin(BigInteger estimatedGas) {
        return estimatedGas.multiply(GAS_MARGIN_NUMERATOR)
                .add(GAS_MARGIN_DENOMINATOR.subtract(BigInteger.ONE))
                .divide(GAS_MARGIN_DENOMINATOR);
    }

    static int expectedEventLogIndex(List<Log> logs, Hash32 expectedTopic, EthereumAddress contractAddress) {
        if (logs == null) {
            return -1;
        }
        for (Log log : logs) {
            if (log == null || log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (!contractAddress.hex().equalsIgnoreCase(log.getAddress())
                    || !expectedTopic.hex().equalsIgnoreCase(log.getTopics().get(0))) {
                continue;
            }
            return asInt(requireUnsigned(log.getLogIndex(), "event log index"), "event log index");
        }
        return -1;
    }

    private static ReceiptLocation receiptLocation(TransactionReceipt receipt) {
        return new ReceiptLocation(
                asLong(requirePositive(receipt.getBlockNumber(), "receipt block number"), "receipt block number"),
                hash(receipt.getBlockHash(), "receipt block hash"));
    }

    private static CredentialChainState credentialState(BigInteger value) {
        return switch (asInt(value, "credential state")) {
            case 0 -> CredentialChainState.NONE;
            case 1 -> CredentialChainState.REVOKED;
            case 2 -> CredentialChainState.SUPERSEDED;
            default -> throw invalidResponse("credential state enum is out of range");
        };
    }

    private static EthereumAddress address(String value, String field) {
        try {
            return EthereumAddress.fromHex(value);
        } catch (RuntimeException exception) {
            throw new BlockchainGatewayException("BLOCKCHAIN_RESPONSE_INVALID", false, field + " is invalid", exception);
        }
    }

    private static Hash32 hash(String value, String field) {
        try {
            return Hash32.fromHex(value);
        } catch (RuntimeException exception) {
            throw new BlockchainGatewayException("BLOCKCHAIN_RESPONSE_INVALID", false, field + " is invalid", exception);
        }
    }

    private static Hash32 hash(byte[] value, String field) {
        try {
            return Hash32.of(value);
        } catch (RuntimeException exception) {
            throw new BlockchainGatewayException("BLOCKCHAIN_RESPONSE_INVALID", false, field + " is invalid", exception);
        }
    }

    private static long asLong(BigInteger value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(LONG_MAX) > 0) {
            throw invalidResponse(field + " does not fit a Java long");
        }
        return value.longValueExact();
    }

    private static int asInt(BigInteger value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(INT_MAX) > 0) {
            throw invalidResponse(field + " does not fit a Java int");
        }
        return value.intValueExact();
    }

    private static BigInteger requireUnsigned(BigInteger value, String field) {
        if (value == null || value.signum() < 0) {
            throw invalidResponse(field + " must be an unsigned integer");
        }
        return value;
    }

    private static BigInteger requirePositive(BigInteger value, String field) {
        if (value == null || value.signum() <= 0) {
            throw invalidResponse(field + " must be greater than zero");
        }
        return value;
    }

    private static boolean isZeroAddress(EthereumAddress address) {
        return address.hex().equals("0x0000000000000000000000000000000000000000");
    }

    private static BlockchainGatewayException configuration(String message) {
        return new BlockchainGatewayException("BLOCKCHAIN_CONFIGURATION_INVALID", false, message);
    }

    private static BlockchainGatewayException invalidResponse(String message) {
        return new BlockchainGatewayException("BLOCKCHAIN_RESPONSE_INVALID", false, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static boolean isAlreadyKnown(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("already known") || normalized.contains("known transaction")
                || normalized.contains("already imported");
    }

    static boolean isDeterministicBroadcastRejection(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("insufficient funds")
                || normalized.contains("intrinsic gas")
                || normalized.contains("invalid sender")
                || normalized.contains("invalid transaction");
    }

    private static <T extends Response<?>> T send(org.web3j.protocol.core.Request<?, T> request) {
        try {
            T response = request.send();
            if (response.hasError()) {
                int code = response.getError().getCode();
                boolean retryable = code != -32600 && code != -32601 && code != -32602;
                throw new BlockchainGatewayException(
                        retryable ? "BLOCKCHAIN_RPC_ERROR" : "BLOCKCHAIN_RPC_REQUEST_REJECTED",
                        retryable,
                        "blockchain RPC rejected the request (code " + code + ")");
            }
            return response;
        } catch (IOException exception) {
            throw new BlockchainGatewayException(
                    "BLOCKCHAIN_RPC_UNAVAILABLE", true, "blockchain RPC could not be reached", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        Web3j current = web3j;
        if (current != null) {
            current.shutdown();
        }
    }

    private record ReceiptLocation(long blockNumber, Hash32 blockHash) {
    }
}
