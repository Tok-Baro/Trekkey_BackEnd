package com.api.trekkey.domain.credential.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.api.trekkey.domain.credential.infrastructure.blockchain.SuiBlockchainAnchorAdapter;
import com.api.trekkey.domain.credential.infrastructure.blockchain.Web3jKaiaBlockchainAnchorAdapter;
import com.api.trekkey.domain.credential.infrastructure.blockchain.BlockchainVerificationRouter;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SuiProviderWiringTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BlockchainConfig.class, SuiBlockchainAnchorAdapter.class,
                    Web3jKaiaBlockchainAnchorAdapter.class, BlockchainVerificationRouter.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test void disabledSuiProfileBindsNativeConfigurationWithoutCreatingAnyKaiaClient() {
        runner.withPropertyValues("blockchain.anchoring.provider=SUI", "blockchain.anchoring.chain-id=0",
                "blockchain.anchoring.sui.network=localnet", "blockchain.anchoring.sui.chain-identifier=aabbccdd")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(BlockchainAnchorPort.class)
                            .hasSingleBean(SuiBlockchainAnchorAdapter.class).doesNotHaveBean(Web3jKaiaBlockchainAnchorAdapter.class);
                    assertThat(context.getBean(BlockchainProperties.class).getSui().getNetwork()).isEqualTo("localnet");
                });
    }

    @Test void legacyDefaultIsStillExplicitlyDisabledAndUsesOnlyTheLegacyAdapter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Web3jKaiaBlockchainAnchorAdapter.class).doesNotHaveBean(SuiBlockchainAnchorAdapter.class);
            assertThat(context.getBean(BlockchainProperties.class).isReadEnabled()).isFalse();
        });
    }

    @Test void enablingAHistoricalReadRouteNeverRegistersAnAlternativeWritePortOrKaiaWriterBean() {
        runner.withPropertyValues("blockchain.anchoring.provider=SUI", "blockchain.anchoring.chain-id=0",
                "blockchain.anchoring.legacy-kaia-read-routes[0].enabled=true",
                "blockchain.anchoring.legacy-kaia-read-routes[0].chain-id=1001",
                "blockchain.anchoring.legacy-kaia-read-routes[0].rpc-url=https://rpc.example.invalid",
                "blockchain.anchoring.legacy-kaia-read-routes[0].contract-address=0x" + "11".repeat(20),
                "blockchain.anchoring.legacy-kaia-read-routes[0].runtime-code-hash=0x" + "22".repeat(32),
                "blockchain.anchoring.legacy-kaia-read-routes[0].contract-version=1")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(BlockchainAnchorPort.class)
                            .hasSingleBean(SuiBlockchainAnchorAdapter.class).hasSingleBean(BlockchainVerificationRouter.class)
                            .doesNotHaveBean(Web3jKaiaBlockchainAnchorAdapter.class);
                    assertThat(context.getBean(BlockchainProperties.class).getLegacyKaiaReadRoutes()).hasSize(1);
                });
    }
}
