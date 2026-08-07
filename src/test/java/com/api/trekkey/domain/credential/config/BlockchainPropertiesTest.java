package com.api.trekkey.domain.credential.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlockchainPropertiesTest {

    @Test
    void disabledDefaultsRemainValidWithoutChainSecrets() {
        BlockchainProperties properties = new BlockchainProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledWriterRejectsMissingContractAndRelayerKeyAtStartup() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contractAddress");

        properties.setContractAddress("0x1111111111111111111111111111111111111111");
        properties.setRuntimeCodeHash("0x" + "22".repeat(32));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("relayerPrivateKey");
    }

    @Test
    void localPrivateKeyRelayerIsRejectedOnKaiaMainnet() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
        properties.setChainId(8217L);
        properties.setContractAddress("0x1111111111111111111111111111111111111111");
        properties.setRuntimeCodeHash("0x" + "22".repeat(32));
        properties.setRelayerPrivateKey("11".repeat(32));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restricted to Kaia Kairos");
    }

    @Test
    void enabledReaderRequiresTheExpectedRegistryRuntimeHash() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.READ_ONLY);
        properties.setContractAddress("0x1111111111111111111111111111111111111111");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtimeCodeHash");
    }
}
