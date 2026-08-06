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

    private Mode mode = Mode.DISABLED;
    private long chainId = 1001L;
    private String rpcUrl = "https://public-en-kairos.node.kaia.io";
    private String contractAddress;
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

    public boolean isReadEnabled() {
        return mode != Mode.DISABLED;
    }

    public boolean isWriteEnabled() {
        return mode == Mode.LOCAL_RELAYER;
    }

    @PostConstruct
    void validate() {
        require(mode != null, "mode is required");
        require(chainId > 0, "chainId must be greater than zero");
        require(notBlank(contractVersion), "contractVersion is required");
        require(treeVersion > 0, "treeVersion must be greater than zero");
        require(batchSize > 0, "batchSize must be greater than zero");
        require(positive(approvalTtl), "approvalTtl must be positive");
        require(workerClaimSize > 0, "workerClaimSize must be greater than zero");
        require(workerMaxAttempts > 0, "workerMaxAttempts must be greater than zero");
        require(positive(outboxLeaseTimeout), "outboxLeaseTimeout must be positive");
        require(positive(receiptPollingInterval), "receiptPollingInterval must be positive");
        require(positive(receiptTimeout), "receiptTimeout must be positive");

        if (!isReadEnabled()) {
            return;
        }
        require(isAbsoluteHttpUrl(rpcUrl), "rpcUrl must be an absolute HTTP(S) URL");
        require(contractAddress != null
                        && contractAddress.matches("0x[0-9a-fA-F]{40}")
                        && !contractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000"),
                "contractAddress must be a non-zero 20-byte hex address");
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
