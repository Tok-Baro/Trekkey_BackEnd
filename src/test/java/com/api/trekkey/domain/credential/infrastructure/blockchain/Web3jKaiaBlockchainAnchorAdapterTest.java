package com.api.trekkey.domain.credential.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.EthGetCode;
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

    @Test
    @SuppressWarnings("unchecked")
    void rejectsARegistryWhoseRuntimeHashDiffersFromConfiguration() throws IOException {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.READ_ONLY);
        properties.setContractAddress(REGISTRY.hex());
        properties.setRuntimeCodeHash("0x" + "22".repeat(32));
        Web3j web3j = mock(Web3j.class);
        Request<?, EthChainId> chainIdRequest = mock(Request.class);
        Request<?, EthGetCode> codeRequest = mock(Request.class);
        EthChainId chainId = new EthChainId();
        chainId.setResult("0x3e9");
        EthGetCode code = new EthGetCode();
        code.setResult("0x6000");
        doReturn(chainIdRequest).when(web3j).ethChainId();
        given(chainIdRequest.send()).willReturn(chainId);
        doReturn(codeRequest)
                .when(web3j)
                .ethGetCode(eq(REGISTRY.hex()), any(DefaultBlockParameter.class));
        given(codeRequest.send()).willReturn(code);
        Web3jKaiaBlockchainAnchorAdapter adapter =
                new Web3jKaiaBlockchainAnchorAdapter(properties, () -> web3j);

        assertThatThrownBy(() -> adapter.getIssuerKey(Hash32.ZERO, 1))
                .isInstanceOf(BlockchainGatewayException.class)
                .hasMessageContaining("runtime code hash");
    }

    private static Log log(String address, String topic, int index) {
        Log log = new Log();
        log.setAddress(address);
        log.setTopics(List.of(topic));
        log.setLogIndex("0x" + Integer.toHexString(index));
        return log;
    }
}
