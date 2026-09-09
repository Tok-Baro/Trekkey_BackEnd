package com.api.trekkey.domain.credential.service.support;

import com.api.trekkey.domain.credential.crypto.ChainAddress;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import java.util.Locale;
import java.util.Objects;

/** Resolves only stored evidence. Runtime provider settings are never historical evidence. */
public record PersistedAnchorIdentity(String provider, long chainId, String contractAddress,
        String contractVersion, String context) {

    public static PersistedAnchorIdentity from(AncBatch batch, AncChainTransaction tx) {
        String batchContext = batch == null ? null : batch.getChainContext();
        String txContext = tx == null ? null : tx.getChainContext();
        PersistedAnchorIdentity batchIdentity = batchContext == null ? null : parse(batchContext);
        PersistedAnchorIdentity txIdentity = txContext == null ? null : parse(txContext);
        if (batchIdentity != null && txIdentity != null && !batchIdentity.equals(txIdentity)) throw invalid();
        PersistedAnchorIdentity identity = txIdentity != null ? txIdentity : batchIdentity;
        if (tx != null) {
            if (tx.getOperationType() != ChainOperationType.ANCHOR_BATCH
                    || (batch != null && !Objects.equals(batch.getId(), tx.getBatchId()))) throw invalid();
            String contract = ChainAddress.of(tx.getContractAddress()).hex();
            if (identity == null) {
                if (tx.getChainId() <= 0 || tx.getContractAddress().length != 20) throw invalid();
                identity = parse("KAIA|" + tx.getChainId() + "|" + contract + "|" + tx.getContractVersion());
            }
            if (identity.chainId != tx.getChainId() || !identity.contractAddress.equals(contract)
                    || !identity.contractVersion.equals(tx.getContractVersion())) throw invalid();
            // A Sui transaction was never created without its registry/network snapshot.
            if (identity.provider.equals("SUI") && (txContext == null || (batch != null && batchContext == null))) throw invalid();
        }
        if (identity == null) throw invalid();
        return identity;
    }

    public static PersistedAnchorIdentity parse(String context) {
        if (context == null) throw invalid();
        String[] parts = context.split("\\|", -1);
        if (parts.length == 4 && parts[0].equals("KAIA") && parts[1].matches("[1-9][0-9]*")
                && nonZeroHex(parts[2], 40) && version(parts[3])) {
            long chain = Long.parseLong(parts[1]);
            String address = parts[2].toLowerCase(Locale.ROOT);
            return new PersistedAnchorIdentity("KAIA", chain, address, parts[3],
                    "KAIA|" + chain + "|" + address + "|" + parts[3]);
        }
        if (parts.length == 6 && parts[0].equals("SUI")
                && java.util.Set.of("localnet", "testnet", "mainnet").contains(parts[1])
                && parts[2].matches("[0-9a-fA-F]{8}") && nonZeroHex(parts[3], 64)
                && nonZeroHex(parts[4], 64) && version(parts[5])) {
            return new PersistedAnchorIdentity("SUI", 0, parts[3].toLowerCase(Locale.ROOT), parts[5],
                    String.join("|", "SUI", parts[1], parts[2].toLowerCase(Locale.ROOT),
                            parts[3].toLowerCase(Locale.ROOT), parts[4].toLowerCase(Locale.ROOT), parts[5]));
        }
        throw invalid();
    }

    public boolean matchesIssuerContext(String stored) {
        // Legacy keys without snapshots are bound by the selected batch/transaction and on-chain signer.
        return stored == null ? provider.equals("KAIA") : equals(parse(stored));
    }

    private static boolean version(String value) {
        return !value.isBlank() && value.length() <= 100 && value.equals(value.trim()) && !value.equals("null");
    }

    private static boolean nonZeroHex(String value, int length) {
        return value.matches("0x[0-9a-fA-F]{" + length + "}") && !value.equals("0x" + "0".repeat(length));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("stored blockchain coordinates are missing, malformed or inconsistent");
    }
}
