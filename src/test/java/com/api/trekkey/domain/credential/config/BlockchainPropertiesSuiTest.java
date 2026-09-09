package com.api.trekkey.domain.credential.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class BlockchainPropertiesSuiTest {
    private static final String PACKAGE_ID = "0x" + "ab".repeat(32);
    private static final String REGISTRY_ID = "0x" + "cd".repeat(32);

    @Test
    void disabledDefaultRemainsKaiaUntilSuiIsExplicitlySelected() {
        BlockchainProperties properties = new BlockchainProperties();
        assertThat(properties.getProvider()).isEqualTo(BlockchainProperties.Provider.KAIA);
        assertThat(properties.isSui()).isFalse();
        assertThat(properties.isReadEnabled()).isFalse();
        assertThat(properties.isWriteEnabled()).isFalse();
        assertThat(properties.ledgerChainId()).isEqualTo(1001L);
        assertThatCode(properties::validate).doesNotThrowAnyException();
        properties.setProvider(BlockchainProperties.Provider.SUI);
        assertThat(properties.isSui()).isTrue();
        assertThat(properties.ledgerChainId()).isZero();
    }

    @Test
    void suiUsesRealPackageWidthAndZeroEvmChainIdSentinel() {
        BlockchainProperties properties = sui();
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.ledgerChainId()).isZero();
        assertThat(properties.ledgerContractAddress()).hasSize(32);
        assertThat(java.util.HexFormat.of().formatHex(properties.ledgerContractAddress())).isEqualTo("ab".repeat(32));
        properties.setChainId(8217L);
        assertThat(properties.ledgerChainId()).isZero();
        assertThat(properties.chainContext()).doesNotContain("8217");
        BlockchainProperties evm = new BlockchainProperties();
        evm.setContractAddress("0x" + "11".repeat(20));
        assertThat(evm.ledgerContractAddress()).hasSize(20);
        assertThat(evm.ledgerChainId()).isEqualTo(1001L);
    }

    @Test
    void contextBindsProviderNetworkIdentifierPackageRegistryAndVersion() {
        BlockchainProperties properties = sui();
        String context = properties.chainContext();
        assertThat(context).isEqualTo("SUI|testnet|a1b2c3d4|" + PACKAGE_ID + "|" + REGISTRY_ID + "|1");
        assertThat(properties.matchesContext(context)).isTrue();
        assertThat(properties.matchesContext(null)).isFalse();
        assertThat(properties.matchesContext("")).isFalse();
        List<Consumer<BlockchainProperties>> changes = List.of(
            p -> p.setProvider(BlockchainProperties.Provider.KAIA),
            p -> p.getSui().setNetwork("localnet"),
            p -> p.getSui().setChainIdentifier("a1b2c3d5"),
            p -> p.getSui().setPackageId("0x" + "12".repeat(32)),
            p -> p.getSui().setRegistryId("0x" + "34".repeat(32)),
            p -> p.setContractVersion("2")
        );
        for (Consumer<BlockchainProperties> change : changes) {
            BlockchainProperties changed = sui();
            change.accept(changed);
            assertThat(changed.matchesContext(context)).isFalse();
        }
        properties.getSui().setChainIdentifier("A1B2C3D4");
        properties.getSui().setPackageId("0x" + "AB".repeat(32));
        properties.getSui().setRegistryId("0x" + "CD".repeat(32));
        assertThat(properties.matchesContext(context)).isTrue();
        properties.getSui().setGatewayUrl("https://gateway.example.test");
        properties.getSui().setGatewayToken("b".repeat(32));
        assertThat(properties.matchesContext(context)).isTrue();
    }

    @Test
    void legacyNullContextIsOnlyAcceptedInKaiaModeAndExplicitCoordinatesCannotDrift() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setContractAddress("0x" + "11".repeat(20));
        assertThat(properties.matchesContext(null)).isTrue();
        String context = properties.chainContext();
        assertThat(properties.matchesContext(context)).isTrue();
        properties.setChainId(8217L);
        assertThat(properties.matchesContext(context)).isFalse();
        properties.setChainId(1001L);
        properties.setContractAddress("0x" + "22".repeat(20));
        assertThat(properties.matchesContext(context)).isFalse();
        assertThat(sui().matchesContext(context)).isFalse();
    }

    @Test
    void allowsTestnetAndLocalnetWritesButRejectsMainnetWrites() {
        for (String network : List.of("localnet", "testnet")) {
            BlockchainProperties properties = sui();
            properties.getSui().setNetwork(network);
            properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
            assertThatCode(properties::validate).doesNotThrowAnyException();
        }
        BlockchainProperties mainnet = sui();
        mainnet.getSui().setNetwork("mainnet");
        assertThatCode(mainnet::validate).doesNotThrowAnyException();
        mainnet.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
        assertThatThrownBy(mainnet::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("mainnet");
    }

    @Test
    void plaintextGatewayIsAllowedOnlyForExplicitLoopbackHosts() {
        for (String origin : List.of("http://localhost:9187", "http://127.0.0.1:9187/", "http://[::1]:9187",
            "https://gateway.example.test", "https://gateway.example.test:9443/")) {
            BlockchainProperties properties = sui();
            properties.getSui().setGatewayUrl(origin);
            assertThatCode(properties::validate).as(origin).doesNotThrowAnyException();
        }
        for (String origin : List.of("http://gateway.example.test", "http://10.0.0.1:9187", "http://0.0.0.0:9187",
            "http://localhost.evil.test:9187", "http://127.0.0.1.evil.test", "http://2130706433:9187")) {
            BlockchainProperties properties = sui();
            properties.getSui().setGatewayUrl(origin);
            assertThatThrownBy(properties::validate).as(origin).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
        }
    }

    @Test
    void rejectsGatewayCredentialsPathsQueriesFragmentsAndNonHttpSchemes() {
        for (String origin : new String[] {null, "", "gateway.example.test", "ftp://gateway.example.test",
            "https://user:password@gateway.example.test", "https://gateway.example.test/v1",
            "https://gateway.example.test?token=secret", "https://gateway.example.test#fragment"}) {
            BlockchainProperties properties = sui();
            properties.getSui().setGatewayUrl(origin);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("gatewayUrl");
        }
    }

    @Test
    void gatewayTokenRequiresAtLeastThirtyTwoCharactersAndNoHeaderLineBreaks() {
        for (String token : new String[] {null, "", "a".repeat(31), "a".repeat(32) + "\n", "a".repeat(32) + "\r"}) {
            BlockchainProperties properties = sui();
            properties.getSui().setGatewayToken(token);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("gatewayToken");
        }
        BlockchainProperties properties = sui();
        properties.getSui().setGatewayToken("a".repeat(32));
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrMalformedSuiCoordinatesAndTimeouts() {
        for (String network : new String[] {null, "", "devnet", "TESTNET"}) {
            BlockchainProperties properties = sui();
            properties.getSui().setNetwork(network);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("network");
        }
        for (String chain : new String[] {null, "", "0xa1b2c3d4", "a1b2c3d", "a1b2c3d45", "a1b2c3dg"}) {
            BlockchainProperties properties = sui();
            properties.getSui().setChainIdentifier(chain);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("chainIdentifier");
        }
        for (String id : new String[] {null, "", "0x2", "0x" + "00".repeat(32), "0x" + "11".repeat(20), "11".repeat(32)}) {
            BlockchainProperties properties = sui();
            properties.getSui().setPackageId(id);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("packageId");
            BlockchainProperties registry = sui();
            registry.getSui().setRegistryId(id);
            assertThatThrownBy(registry::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("registryId");
        }
        for (Duration timeout : new Duration[] {null, Duration.ZERO, Duration.ofMillis(-1)}) {
            BlockchainProperties properties = sui();
            properties.getSui().setRequestTimeout(timeout);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("requestTimeout");
        }
    }

    private static BlockchainProperties sui() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setProvider(BlockchainProperties.Provider.SUI);
        properties.setMode(BlockchainProperties.Mode.READ_ONLY);
        properties.getSui().setChainIdentifier("a1b2c3d4");
        properties.getSui().setPackageId(PACKAGE_ID);
        properties.getSui().setRegistryId(REGISTRY_ID);
        properties.getSui().setGatewayToken("a".repeat(32));
        return properties;
    }
}
