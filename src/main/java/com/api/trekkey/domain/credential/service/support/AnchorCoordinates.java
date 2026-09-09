package com.api.trekkey.domain.credential.service.support;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.ChainAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.SuiDigest;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView.ChainMetadata;

/** Displays persisted coordinates; a provider configuration change must never rewrite old evidence. */
public record AnchorCoordinates(long chainId, String contractAddress, String transactionHash,
        Long blockNumber, ChainMetadata metadata) {
    public static AnchorCoordinates from(AncBatch batch, AncChainTransaction tx, BlockchainProperties config) {
        try {
            return storedOrIntended(batch, tx, config);
        } catch (IllegalArgumentException exception) {
            // Malformed persistence must produce a fail-closed verification response, never a 500 or
            // a current-provider fallback presented as the historical deployment.
            return new AnchorCoordinates(0, null, null, null,
                    new ChainMetadata(null, null, null, null, null, null, null, null, null, null));
        }
    }

    private static AnchorCoordinates storedOrIntended(AncBatch batch, AncChainTransaction tx, BlockchainProperties config) {
        if (batch == null && tx == null && config.isSui()) {
            // No usable persisted coordinates were supplied: provider/network describe intent only. Do not
            // advertise configured package/registry, an approval, or transaction coordinates as evidence.
            // A legacy batch/transaction with no context must still follow the Kaia path below.
            return new AnchorCoordinates(0, null, null, null,
                    new ChainMetadata("SUI", config.getSui().getNetwork(), null, null, null,
                            null, null, null, null, null));
        }
        String context = tx != null && tx.getChainContext() != null ? tx.getChainContext()
                : batch == null ? null : batch.getChainContext();
        if (context != null || tx != null) context = PersistedAnchorIdentity.from(batch, tx).context();
        if (context != null && context.startsWith("SUI|")) {
            String[] parts = context.split("\\|", -1);
            if (parts.length != 6) throw new IllegalArgumentException("invalid stored Sui context");
            String digest = tx == null || tx.getTxHash() == null ? null : SuiDigest.encode(tx.getTxHash());
            Long checkpoint = tx == null ? null : tx.getBlockNumber();
            String checkpointDigest = tx == null || tx.getBlockHash() == null ? null : SuiDigest.encode(tx.getBlockHash());
            String explorer = digest == null || parts[1].equals("localnet") ? null
                    : "https://suiscan.xyz/" + parts[1] + "/tx/" + digest;
            return new AnchorCoordinates(0, parts[3], digest, checkpoint,
                    new ChainMetadata("SUI", parts[1], parts[2], parts[3], parts[4], digest,
                            checkpoint, checkpointDigest, explorer, "TREKKEY_SUI_APPROVAL_V1"));
        }
        long chain = tx != null ? tx.getChainId() : batch == null && !config.isSui() ? config.getChainId() : 0;
        String contract = tx != null ? ChainAddress.of(tx.getContractAddress()).hex()
                : batch == null && !config.isSui() ? config.getContractAddress() : null;
        if (context != null && context.startsWith("KAIA|")) {
            String[] parts = context.split("\\|", -1);
            if (parts.length != 4) throw new IllegalArgumentException("invalid stored Kaia context");
            chain = Long.parseLong(parts[1]);
            contract = parts[2];
        }
        String hash = tx == null || tx.getTxHash() == null ? null : Hash32.of(tx.getTxHash()).hex();
        String explorer = hash == null ? null : chain == 1001 ? "https://kairos.kaiascan.io/tx/" + hash
                : chain == 8217 ? "https://kaiascan.io/tx/" + hash : null;
        return new AnchorCoordinates(chain, contract, hash, tx == null ? null : tx.getBlockNumber(),
                new ChainMetadata("KAIA", chain == 1001 ? "kairos" : chain == 8217 ? "mainnet" : null,
                        null, null, null, null, null, null, explorer, "EIP712_V1"));
    }
}
