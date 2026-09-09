package com.api.trekkey.domain.credential.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "blockchain.anchoring")
public class BlockchainProperties {

    private Provider provider = Provider.KAIA;
    private Sui sui = new Sui();
    private Mode mode = Mode.DISABLED;
    private long chainId = 1001L;
    private String rpcUrl = "https://public-en-kairos.node.kaia.io";
    private String contractAddress;
    private String runtimeCodeHash;
    private String contractVersion = "1";
    private int treeVersion = 1;
    private int batchSize = 100;
    private Duration approvalTtl = Duration.ofMinutes(15);
    private boolean workerEnabled = false;
    private int workerClaimSize = 10;
    private int workerMaxAttempts = 8;
    private Duration outboxLeaseTimeout = Duration.ofMinutes(1);
    private Duration receiptPollingInterval = Duration.ofSeconds(2);
    private Duration receiptTimeout = Duration.ofMinutes(2);
    private String relayerPrivateKey;
    /** Read-only historical deployments. Never used for signing, approval or outbox work. */
    private java.util.List<LegacyKaiaReadRoute> legacyKaiaReadRoutes = new java.util.ArrayList<>();

    public boolean isReadEnabled() {
        return mode != Mode.DISABLED;
    }

    public boolean isWriteEnabled() {
        return mode == Mode.LOCAL_RELAYER;
    }

    public boolean isSui() { return provider == Provider.SUI; }

    /** Zero is a legacy API sentinel: Sui does NOT have an EVM chainId. */
    public long ledgerChainId() { return isSui() ? 0 : chainId; }

    public byte[] ledgerContractAddress() {
        return isSui()
                ? com.api.trekkey.domain.credential.crypto.Hash32.fromHex(sui.packageId).bytes()
                : com.api.trekkey.domain.credential.crypto.EthereumAddress.fromHex(contractAddress).bytes();
    }

    /** Immutable routing/domain identity persisted before signing, including for unsigned batches. */
    public String chainContext() {
        return isSui()
                ? String.join("|", "SUI", sui.network, sui.chainIdentifier.toLowerCase(),
                        sui.packageId.toLowerCase(), sui.registryId.toLowerCase(), contractVersion)
                : String.join("|", "KAIA", Long.toString(chainId),
                        contractAddress == null ? "" : contractAddress.toLowerCase(), contractVersion);
    }

    public boolean matchesContext(String stored) {
        // Only historical EVM rows may lack the new coordinate snapshot. Never reinterpret them as Sui.
        return stored == null ? !isSui() : stored.equals(chainContext());
    }

    @PostConstruct
    void validate() {
        require(mode != null, "mode is required");
        require(provider != null && sui != null, "provider and sui configuration are required");
        require(isSui() || chainId > 0, "chainId must be greater than zero for Kaia");
        require(notBlank(contractVersion), "contractVersion is required");
        require(treeVersion > 0, "treeVersion must be greater than zero");
        require(batchSize > 0, "batchSize must be greater than zero");
        require(positive(approvalTtl), "approvalTtl must be positive");
        require(workerClaimSize > 0, "workerClaimSize must be greater than zero");
        require(workerMaxAttempts > 0, "workerMaxAttempts must be greater than zero");
        require(positive(outboxLeaseTimeout), "outboxLeaseTimeout must be positive");
        require(positive(receiptPollingInterval), "receiptPollingInterval must be positive");
        require(positive(receiptTimeout), "receiptTimeout must be positive");
        require(legacyKaiaReadRoutes != null, "legacyKaiaReadRoutes is required");
        java.util.Set<String> readContexts = new java.util.HashSet<>();
        if (isReadEnabled() && !isSui()) readContexts.add(chainContext());
        for (LegacyKaiaReadRoute route : legacyKaiaReadRoutes) {
            require(route != null, "legacyKaiaReadRoutes must not contain null entries");
            if (!route.enabled) continue;
            BlockchainProperties reader = route.readOnlyProperties();
            require(readContexts.add(reader.chainContext()), "duplicate or ambiguous legacy Kaia read route");
        }

        if (!isReadEnabled()) {
            return;
        }
        if (isSui()) {
            require(sui.network != null && java.util.Set.of("localnet", "testnet", "mainnet").contains(sui.network),
                    "sui.network must be localnet, testnet or mainnet");
            require(sui.chainIdentifier != null && sui.chainIdentifier.matches("[0-9a-fA-F]{8}"),
                    "sui.chainIdentifier must be the real 4-byte network identifier");
            require(suiAddress(sui.packageId) && suiAddress(sui.registryId), "sui packageId and registryId must be non-zero 32-byte IDs");
            require(isAbsoluteHttpUrl(sui.gatewayUrl), "sui.gatewayUrl must be an absolute HTTP(S) URL");
            URI gateway = URI.create(sui.gatewayUrl);
            require(gateway.getUserInfo() == null && gateway.getQuery() == null && gateway.getFragment() == null
                            && (gateway.getPath().isEmpty() || gateway.getPath().equals("/")),
                    "sui.gatewayUrl must be an origin without credentials, query or path");
            require("https".equalsIgnoreCase(gateway.getScheme())
                            || java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(gateway.getHost()),
                    "plaintext Sui gateway is restricted to loopback; use HTTPS remotely");
            require(sui.gatewayToken != null && sui.gatewayToken.matches("[!-~]{32,4096}"),
                    "sui.gatewayToken must contain 32 to 4096 printable ASCII characters without whitespace");
            require(positive(sui.requestTimeout), "sui.requestTimeout must be positive");
            if (isWriteEnabled()) require(!"mainnet".equals(sui.network), "local Sui relayer is not approved for mainnet");
            return;
        }
        require(isAbsoluteHttpUrl(rpcUrl), "rpcUrl must be an absolute HTTP(S) URL");
        require(contractAddress != null
                        && contractAddress.matches("0x[0-9a-fA-F]{40}")
                        && !contractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000"),
                "contractAddress must be a non-zero 20-byte hex address");
        require(runtimeCodeHash != null
                        && runtimeCodeHash.matches("0x[0-9a-fA-F]{64}")
                        && !runtimeCodeHash.equalsIgnoreCase("0x" + "0".repeat(64)),
                "runtimeCodeHash must be a non-zero 32-byte hex hash");
        if (isWriteEnabled()) {
            require(chainId == 1001L, "LOCAL_RELAYER is restricted to Kaia Kairos");
            String key = relayerPrivateKey == null ? "" : relayerPrivateKey.replaceFirst("^0x", "");
            require(key.matches("[0-9a-fA-F]{64}"), "relayerPrivateKey must be a 32-byte hex key");
        }
    }

    private boolean isAbsoluteHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    private boolean suiAddress(String value) {
        return value != null && value.matches("0x[0-9a-fA-F]{64}") && !value.equals("0x" + "0".repeat(64));
    }

    public enum Provider { KAIA, SUI }

    @Getter
    @Setter
    public static class LegacyKaiaReadRoute {
        private boolean enabled;
        private long chainId;
        private String rpcUrl;
        private String contractAddress;
        private String runtimeCodeHash;
        private String contractVersion;

        /** A separate configuration object with no relayer key and no write capability. */
        public BlockchainProperties readOnlyProperties() {
            if (!enabled || chainId != 1001L) {
                throw new IllegalStateException("Legacy Kaia read route must be enabled and explicitly target Kairos chain 1001");
            }
            BlockchainProperties reader = new BlockchainProperties();
            reader.setMode(Mode.READ_ONLY);
            reader.setChainId(chainId);
            reader.setRpcUrl(rpcUrl);
            reader.setContractAddress(contractAddress);
            reader.setContractVersion(contractVersion);
            reader.setRuntimeCodeHash(runtimeCodeHash);
            reader.validate();
            URI uri = URI.create(rpcUrl);
            if (uri.getUserInfo() != null || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                        || java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()))) {
                throw new IllegalStateException("Legacy Kaia RPC requires HTTPS (or loopback HTTP), without userinfo or fragment");
            }
            return reader;
        }
    }

    @Getter
    @Setter
    public static class Sui {
        private String network = "testnet";
        private String chainIdentifier;
        private String packageId;
        private String registryId;
        private String gatewayUrl = "http://127.0.0.1:9187";
        private String gatewayToken;
        private Duration requestTimeout = Duration.ofSeconds(15);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid blockchain.anchoring configuration: " + message);
        }
    }

    public enum Mode {
        DISABLED,
        READ_ONLY,
        LOCAL_RELAYER
    }
}
