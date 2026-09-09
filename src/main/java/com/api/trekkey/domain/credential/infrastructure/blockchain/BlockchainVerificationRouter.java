package com.api.trekkey.domain.credential.infrastructure.blockchain;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import com.api.trekkey.domain.credential.service.port.BlockchainVerificationReader;
import com.api.trekkey.domain.credential.service.support.PersistedAnchorIdentity;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Explicit allowlist used only by public verification; the active write adapter remains a single bean. */
@Component
public class BlockchainVerificationRouter {
    private final BlockchainProperties properties;
    private final BlockchainVerificationReader activeReader;
    private final List<Route> historical = new ArrayList<>();
    private final List<Web3jKaiaBlockchainAnchorAdapter> ownedAdapters = new ArrayList<>();

    @Autowired
    public BlockchainVerificationRouter(BlockchainProperties properties, BlockchainAnchorPort active) {
        this(properties, active, Web3jKaiaBlockchainAnchorAdapter::new);
    }

    BlockchainVerificationRouter(BlockchainProperties properties, BlockchainAnchorPort active,
            Function<BlockchainProperties, BlockchainAnchorPort> legacyFactory) {
        this.properties = properties;
        this.activeReader = readOnly(active);
        for (var route : properties.getLegacyKaiaReadRoutes()) {
            if (!route.isEnabled()) continue;
            BlockchainProperties config = route.readOnlyProperties();
            BlockchainAnchorPort adapter = legacyFactory.apply(config);
            if (adapter instanceof Web3jKaiaBlockchainAnchorAdapter kaia) ownedAdapters.add(kaia);
            historical.add(new Route(PersistedAnchorIdentity.parse(config.chainContext()), readOnly(adapter)));
        }
    }

    public Route resolve(AncBatch batch, AncChainTransaction transaction) {
        if (!properties.isReadEnabled()) throw configuration("Blockchain verification is disabled");
        try {
            PersistedAnchorIdentity identity = PersistedAnchorIdentity.from(batch, transaction);
            List<Route> candidates = new ArrayList<>();
            if (identity.equals(PersistedAnchorIdentity.parse(properties.chainContext()))) {
                candidates.add(new Route(identity, activeReader));
            }
            historical.stream().filter(route -> route.identity.equals(identity)).forEach(candidates::add);
            if (candidates.size() != 1) throw configuration("Stored deployment has no unique enabled verification route");
            return candidates.getFirst();
        } catch (IllegalArgumentException exception) {
            throw configuration("Stored blockchain coordinates cannot be verified against the read allowlist");
        }
    }

    public record Route(PersistedAnchorIdentity identity, BlockchainVerificationReader reader) {}

    private static BlockchainVerificationReader readOnly(BlockchainAnchorPort delegate) {
        return new BlockchainVerificationReader() {
            public BlockchainAnchorPort.OnChainIssuerKey getIssuerKey(Hash32 issuer, long version) {
                return delegate.getIssuerKey(issuer, version);
            }
            public BlockchainAnchorPort.OnChainBatch getBatch(Hash32 batch) { return delegate.getBatch(batch); }
            public BlockchainAnchorPort.OnChainCredentialStatus getCredentialStatus(Hash32 issuer, Hash32 credential) {
                return delegate.getCredentialStatus(issuer, credential);
            }
        };
    }

    private static BlockchainGatewayException configuration(String message) {
        return new BlockchainGatewayException("BLOCKCHAIN_VERIFICATION_ROUTE_INVALID", false, message);
    }

    @PreDestroy
    void closeReaders() { ownedAdapters.forEach(Web3jKaiaBlockchainAnchorAdapter::shutdown); }
}
