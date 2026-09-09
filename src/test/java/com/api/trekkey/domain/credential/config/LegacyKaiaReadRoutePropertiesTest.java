package com.api.trekkey.domain.credential.config;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class LegacyKaiaReadRoutePropertiesTest {
    @Test void enabledHistoricalReaderRequiresAllExplicitDeploymentAndRpcCoordinates() {
        List<Consumer<BlockchainProperties.LegacyKaiaReadRoute>> mutations = List.of(
                route -> route.setChainId(0), route -> route.setChainId(8217),
                route -> route.setRpcUrl(null), route -> route.setContractAddress(null),
                route -> route.setContractVersion(null), route -> route.setRuntimeCodeHash(null),
                route -> route.setContractAddress("0x" + "00".repeat(20)),
                route -> route.setRuntimeCodeHash("0x" + "00".repeat(32)),
                route -> route.setRpcUrl("http://rpc.example.invalid"),
                route -> route.setRpcUrl("https://user:password@rpc.example.invalid"));
        for (var mutation : mutations) {
            var properties = new BlockchainProperties(); var route = valid(); mutation.accept(route);
            properties.getLegacyKaiaReadRoutes().add(route);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void disabledPartialEntryDoesNotEnableAnImplicitDeployment() {
        var properties = new BlockchainProperties();
        properties.getLegacyKaiaReadRoutes().add(new BlockchainProperties.LegacyKaiaReadRoute());
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
    @Test void duplicateLegacyOrActiveKaiaRouteIsRejectedAtStartup() {
        var properties = new BlockchainProperties(); properties.getLegacyKaiaReadRoutes().add(valid());
        properties.getLegacyKaiaReadRoutes().add(valid());
        assertThatThrownBy(properties::validate).hasMessageContaining("ambiguous");
        properties.getLegacyKaiaReadRoutes().removeLast();
        properties.setMode(BlockchainProperties.Mode.READ_ONLY); properties.setContractAddress(valid().getContractAddress());
        properties.setRuntimeCodeHash(valid().getRuntimeCodeHash());
        assertThatThrownBy(properties::validate).hasMessageContaining("ambiguous");
    }
    @Test void readOnlyCopiesContainNoWriterCapabilityOrRelayerSecret() {
        var reader = valid().readOnlyProperties();
        assertThat(reader.getProvider()).isEqualTo(BlockchainProperties.Provider.KAIA);
        assertThat(reader.isReadEnabled()).isTrue(); assertThat(reader.isWriteEnabled()).isFalse();
        assertThat(reader.isWorkerEnabled()).isFalse(); assertThat(reader.getRelayerPrivateKey()).isNull();
        assertThat(reader.getLegacyKaiaReadRoutes()).isEmpty();
    }
    private static BlockchainProperties.LegacyKaiaReadRoute valid() {
        var route = new BlockchainProperties.LegacyKaiaReadRoute(); route.setEnabled(true);
        route.setChainId(1001); route.setRpcUrl("https://rpc.example.invalid");
        route.setContractAddress("0x" + "11".repeat(20)); route.setContractVersion("1");
        route.setRuntimeCodeHash("0x" + "22".repeat(32)); return route;
    }
}
