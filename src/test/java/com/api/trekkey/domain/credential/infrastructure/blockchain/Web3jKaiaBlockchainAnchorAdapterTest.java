package com.api.trekkey.domain.credential.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;

class Web3jKaiaBlockchainAnchorAdapterTest {

    private static final EthereumAddress REGISTRY =
            EthereumAddress.fromHex("0x00000000000000000000000000000000000000aa");

    @Test
    void acceptsOnlyTheExpectedTopicFromTheConfiguredRegistry() {
        Log wrongContract = log(
                "0x00000000000000000000000000000000000000bb",
                TrekkeyRegistryAbi.BATCH_ANCHORED_TOPIC.hex(),
                1);
        Log wrongTopic = log(REGISTRY.hex(), TrekkeyRegistryAbi.CREDENTIAL_REVOKED_TOPIC.hex(), 2);
        Log expected = log(REGISTRY.hex(), TrekkeyRegistryAbi.BATCH_ANCHORED_TOPIC.hex(), 3);

        assertThat(Web3jKaiaBlockchainAnchorAdapter.expectedEventLogIndex(
                List.of(wrongContract, wrongTopic, expected), TrekkeyRegistryAbi.BATCH_ANCHORED_TOPIC, REGISTRY))
                .isEqualTo(3);
    }

    @Test
    void rejectsReceiptsWithNoExpectedRegistryEvidence() {
        Log wrongTopic = log(REGISTRY.hex(), TrekkeyRegistryAbi.CREDENTIAL_REVOKED_TOPIC.hex(), 2);

        assertThat(Web3jKaiaBlockchainAnchorAdapter.expectedEventLogIndex(
                List.of(wrongTopic), TrekkeyRegistryAbi.BATCH_ANCHORED_TOPIC, REGISTRY))
                .isNegative();
    }

    @Test
    void recognizesIdempotentKnownTransactionResponses() {
        assertThat(Web3jKaiaBlockchainAnchorAdapter.isAlreadyKnown("already known"))
                .isTrue();
        assertThat(Web3jKaiaBlockchainAnchorAdapter.isAlreadyKnown("known transaction"))
                .isTrue();
        assertThat(Web3jKaiaBlockchainAnchorAdapter.isAlreadyKnown("nonce too low"))
                .isFalse();
    }

    @Test
    void keepsAmbiguousBroadcastErrorsOutOfTheDefiniteRejectionPath() {
        assertThat(Web3jKaiaBlockchainAnchorAdapter.isDeterministicBroadcastRejection("nonce too low"))
                .isFalse();
        assertThat(Web3jKaiaBlockchainAnchorAdapter.isDeterministicBroadcastRejection(
                        "replacement transaction underpriced"))
                .isFalse();
        assertThat(Web3jKaiaBlockchainAnchorAdapter.isDeterministicBroadcastRejection("insufficient funds"))
                .isTrue();
        assertThat(Web3jKaiaBlockchainAnchorAdapter.isDeterministicBroadcastRejection("gateway timeout"))
                .isFalse();
    }

    private static Log log(String address, String topic, int index) {
        Log log = new Log();
        log.setAddress(address);
        log.setTopics(List.of(topic));
        log.setLogIndex("0x" + Integer.toHexString(index));
        return log;
    }
}
