package com.api.trekkey.domain.credential.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Signature65;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Type;

class TrekkeyRegistryAbiTest {

    private static final Hash32 ONE = hash("11");
    private static final Hash32 TWO = hash("22");
    private static final Hash32 THREE = hash("33");
    private static final Hash32 FOUR = hash("44");
    private static final Hash32 FIVE = hash("55");

    @Test
    void encodesRegistryFunctionSelectorsFromTheSoliditySignatures() {
        assertThat(TrekkeyRegistryAbi.encode(TrekkeyRegistryAbi.getIssuerKey(ONE, 7))).startsWith("0xb3094d73");
        assertThat(TrekkeyRegistryAbi.encode(TrekkeyRegistryAbi.getBatch(ONE))).startsWith("0xed42136f");
        assertThat(TrekkeyRegistryAbi.encode(TrekkeyRegistryAbi.getCredentialStatus(ONE, TWO))).startsWith("0x2268c8af");
        assertThat(TrekkeyRegistryAbi.encode(TrekkeyRegistryAbi.anchorBatch(batchApproval(), signature())))
                .startsWith("0xe5a5b680");
        assertThat(TrekkeyRegistryAbi.encode(TrekkeyRegistryAbi.revokeCredential(revokeApproval(), signature())))
                .startsWith("0x15b56a3a");
        assertThat(TrekkeyRegistryAbi.encode(TrekkeyRegistryAbi.supersedeCredential(supersedeApproval(), signature())))
                .startsWith("0xcc3b9681");
    }

    @Test
    void decodesTheStaticIssuerKeyTupleFromTheExactAbiShape() {
        String encoded = "0x"
                + word("00000000000000000000000000000000000000aa")
                + word("05")
                + word("06")
                + word("07");

        List<Type> values = FunctionReturnDecoder.decode(
                encoded, TrekkeyRegistryAbi.getIssuerKey(ONE, 1).getOutputParameters());

        assertThat(values).hasSize(1);
        assertThat(values.get(0)).isInstanceOf(TrekkeyRegistryAbi.IssuerKeyTuple.class);
        TrekkeyRegistryAbi.IssuerKeyTuple tuple = (TrekkeyRegistryAbi.IssuerKeyTuple) values.get(0);
        assertThat(tuple.signer.getValue()).isEqualTo("0x00000000000000000000000000000000000000aa");
        assertThat(tuple.validFrom.getValue()).isEqualTo(BigInteger.valueOf(5));
        assertThat(tuple.validUntil.getValue()).isEqualTo(BigInteger.valueOf(6));
        assertThat(tuple.compromisedAt.getValue()).isEqualTo(BigInteger.valueOf(7));
    }

    @Test
    void decodesTheStaticBatchAndStatusTuplesFromTheExactAbiShape() {
        String batchEncoded = "0x"
                + word(ONE.hex().substring(2))
                + word(TWO.hex().substring(2))
                + word(THREE.hex().substring(2))
                + word("04")
                + word("05")
                + word("06")
                + word("07")
                + word("01");
        List<Type> batchValues = FunctionReturnDecoder.decode(
                batchEncoded, TrekkeyRegistryAbi.getBatch(ONE).getOutputParameters());

        assertThat(batchValues).hasSize(8);
        TrekkeyRegistryAbi.BatchReadResult batchResult = TrekkeyRegistryAbi.decodeBatchResult(batchValues);
        TrekkeyRegistryAbi.BatchTuple batch = batchResult.batch();
        assertThat(batch.issuerId.getValue()).containsExactly(ONE.bytes());
        assertThat(batch.merkleRoot.getValue()).containsExactly(TWO.bytes());
        assertThat(batch.schemaVersionHash.getValue()).containsExactly(THREE.bytes());
        assertThat(batch.leafCount.getValue()).isEqualTo(BigInteger.valueOf(4));
        assertThat(batch.treeVersion.getValue()).isEqualTo(BigInteger.valueOf(5));
        assertThat(batch.issuerKeyVersion.getValue()).isEqualTo(BigInteger.valueOf(6));
        assertThat(batch.anchoredAt.getValue()).isEqualTo(BigInteger.valueOf(7));
        assertThat(batchResult.exists()).isTrue();

        String statusEncoded = "0x"
                + word("02")
                + word("08")
                + word("09")
                + word("0a")
                + word(FOUR.hex().substring(2));
        List<Type> statusValues = FunctionReturnDecoder.decode(
                statusEncoded, TrekkeyRegistryAbi.getCredentialStatus(ONE, TWO).getOutputParameters());

        assertThat(statusValues).hasSize(1);
        assertThat(statusValues.get(0)).isInstanceOf(TrekkeyRegistryAbi.StatusTuple.class);
        TrekkeyRegistryAbi.StatusTuple status = (TrekkeyRegistryAbi.StatusTuple) statusValues.get(0);
        assertThat(status.state.getValue()).isEqualTo(BigInteger.TWO);
        assertThat(status.effectiveAt.getValue()).isEqualTo(BigInteger.valueOf(8));
        assertThat(status.recordedAt.getValue()).isEqualTo(BigInteger.valueOf(9));
        assertThat(status.issuerKeyVersion.getValue()).isEqualTo(BigInteger.TEN);
        assertThat(status.replacementCredentialIdHash.getValue()).containsExactly(FOUR.bytes());
    }

    @Test
    void exposesTheExactRegistryEventTopics() {
        assertThat(TrekkeyRegistryAbi.BATCH_ANCHORED_TOPIC.hex())
                .isEqualTo("0x57255e3e321474ff402755e96e420b79f4a0e8811f2784fdba35611108249377");
        assertThat(TrekkeyRegistryAbi.CREDENTIAL_REVOKED_TOPIC.hex())
                .isEqualTo("0x90a2a8b2999242bb199b425115d5b613f1dc528ef37819c63d183ee105688863");
        assertThat(TrekkeyRegistryAbi.CREDENTIAL_SUPERSEDED_TOPIC.hex())
                .isEqualTo("0xe15fa47f23bbba96493a2dc05528411c69e7e7e8ba8b8cab8e2087a34fa4e16c");
    }

    private static Eip712.BatchApproval batchApproval() {
        return new Eip712.BatchApproval(
                ONE, TWO, THREE, FOUR,
                BigInteger.valueOf(3), BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.valueOf(100));
    }

    private static Eip712.StatusApproval revokeApproval() {
        return new Eip712.StatusApproval(
                ONE, TWO, Eip712.StatusAction.REVOKE, Hash32.ZERO,
                BigInteger.ONE, BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(100));
    }

    private static Eip712.StatusApproval supersedeApproval() {
        return new Eip712.StatusApproval(
                ONE, TWO, Eip712.StatusAction.SUPERSEDE, FIVE,
                BigInteger.ONE, BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(100));
    }

    private static Signature65 signature() {
        byte[] signature = new byte[Signature65.LENGTH];
        signature[31] = 1;
        signature[63] = 1;
        signature[64] = 27;
        return Signature65.of(signature);
    }

    private static Hash32 hash(String byteValue) {
        return Hash32.fromHex("0x" + byteValue.repeat(32));
    }

    private static String word(String value) {
        return "0".repeat(64 - value.length()) + value;
    }
}
