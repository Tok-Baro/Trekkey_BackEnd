package com.api.trekkey.domain.credential.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

class SuiApprovalTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PACKAGE_ID = "0x" + "11".repeat(32);
    private static final String REGISTRY_ID = "0x" + "22".repeat(32);
    private static final SuiApproval.Domain DOMAIN = new SuiApproval.Domain("4c78adac", PACKAGE_ID, REGISTRY_ID);
    private static final ECKeyPair TEST_KEY = ECKeyPair.create(BigInteger.ONE);
    private static final EthereumAddress SIGNER = EthereumAddress.fromPublicKey(TEST_KEY.getPublicKey());
    private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    @Test
    void reproducesTheSharedIndependentSuiApprovalFixtureByteForByte() throws Exception {
        JsonNode fixture = OBJECT_MAPPER.readTree(Files.readString(
            Path.of("contracts-sui/test-fixtures/approval-v1.json"), StandardCharsets.UTF_8
        ));
        JsonNode domainNode = fixture.get("domain");
        SuiApproval.Domain domain = new SuiApproval.Domain(
            domainNode.get("chainIdentifier").asText(), domainNode.get("packageId").asText(),
            domainNode.get("registryId").asText()
        );
        assertThat(fixture.get("format").asText()).isEqualTo(SuiApproval.SCHEME);
        assertThat(SuiApproval.domainHash(domain).hex()).isEqualTo(fixture.get("domainHash").asText());
        assertThat(SIGNER.hex()).isEqualTo(fixture.get("signer").asText());
        for (String kind : List.of("batch", "status")) {
            JsonNode expected = fixture.get(kind);
            JsonNode approvalNode = expected.get("approval");
            Hash32 structHash = kind.equals("batch")
                ? Eip712.batchStructHash(batch(approvalNode)) : Eip712.statusStructHash(status(approvalNode));
            Hash32 digest = kind.equals("batch")
                ? SuiApproval.batchDigest(domain, batch(approvalNode)) : SuiApproval.statusDigest(domain, status(approvalNode));
            String payload = kind.equals("batch")
                ? SuiApproval.batchPayloadJson(domain, batch(approvalNode))
                : SuiApproval.statusPayloadJson(domain, status(approvalNode));
            assertThat(structHash.hex()).isEqualTo(expected.get("structHash").asText());
            assertThat(HexCodec.encode(HexCodec.concat(
                new byte[] {0x19, 0x01}, SuiApproval.domainHash(domain).bytes(), structHash.bytes()
            ))).isEqualTo(expected.get("message").asText());
            assertThat(digest.hex()).isEqualTo(expected.get("digest").asText());
            Signature65 signature = Signature65.fromHex(expected.get("signature").asText());
            assertThat(Eip712.recoverSigner(digest, signature)).isEqualTo(SIGNER);
            assertThat(Eip712.signDigest(digest, TEST_KEY).hex()).isEqualTo(signature.hex());
            JsonNode payloadNode = OBJECT_MAPPER.readTree(payload);
            assertThat(payloadNode.get("domain")).isEqualTo(domainNode);
            assertThat(payloadNode.get("message")).isEqualTo(approvalNode);
            assertThat(payloadNode.get("digestHex").asText()).isEqualTo(expected.get("digest").asText());
        }
    }

    @Test
    void normalizesDomainWithoutChangingItsBytes() {
        SuiApproval.Domain normalized = new SuiApproval.Domain(
            "0x4C78ADAC", "0x" + "AB".repeat(32), "0x" + "CD".repeat(32)
        );
        assertThat(normalized.chainIdentifier()).isEqualTo("4c78adac");
        assertThat(normalized.packageId()).isEqualTo("0x" + "ab".repeat(32));
        assertThat(normalized.registryId()).isEqualTo("0x" + "cd".repeat(32));
        assertThat(SuiApproval.domainHash(normalized)).isEqualTo(SuiApproval.domainHash(
            new SuiApproval.Domain("4c78adac", normalized.packageId(), normalized.registryId())
        ));
        assertThat(SuiApproval.domainHash(new SuiApproval.Domain("0X4C78ADAC", PACKAGE_ID, REGISTRY_ID)))
            .isEqualTo(SuiApproval.domainHash(DOMAIN));
    }

    @Test
    void rejectsMalformedDomainsAndZeroObjectIds() {
        for (String chain : new String[] {null, "", "4c78ada", "4c78adacc", "gg78adac", " 4c78adac", "４c78adac"}) {
            assertThatThrownBy(() -> new SuiApproval.Domain(chain, PACKAGE_ID, REGISTRY_ID))
                .isInstanceOf(CryptoValidationException.class).hasMessageContaining("chainIdentifier");
        }
        for (String id : new String[] {null, "", "11".repeat(32), "0x1", "0x" + "00".repeat(32),
            "0x" + "gg".repeat(32), "0x" + "11".repeat(33), "0x" + "１１".repeat(32)}) {
            assertThatThrownBy(() -> new SuiApproval.Domain("4c78adac", id, REGISTRY_ID))
                .isInstanceOf(CryptoValidationException.class).hasMessageContaining("packageId");
            assertThatThrownBy(() -> new SuiApproval.Domain("4c78adac", PACKAGE_ID, id))
                .isInstanceOf(CryptoValidationException.class).hasMessageContaining("registryId");
        }
        assertThatThrownBy(() -> SuiApproval.domainHash(null)).isInstanceOf(CryptoValidationException.class);
        assertThatThrownBy(() -> SuiApproval.batchPayloadJson(DOMAIN, null)).isInstanceOf(CryptoValidationException.class);
        assertThatThrownBy(() -> SuiApproval.statusPayloadJson(DOMAIN, null)).isInstanceOf(CryptoValidationException.class);
    }

    @Test
    void usesRawFourByteChainAndThirtyTwoByteObjectIdsWithExistingStructHashes() {
        Hash32 expectedDomain = Hashing.keccak256(HexCodec.concat(
            "TREKKEY_SUI_APPROVAL_V1".getBytes(StandardCharsets.UTF_8),
            new byte[] {0x4c, 0x78, (byte) 0xad, (byte) 0xac},
            Hash32.fromHex(PACKAGE_ID).bytes(), Hash32.fromHex(REGISTRY_ID).bytes()
        ));
        assertThat(SuiApproval.domainHash(DOMAIN)).isEqualTo(expectedDomain);
        assertThat(Eip712.batchStructHash(batch()).hex())
            .isEqualTo("0x99983d30fc6a20f3231962cec593c737bc07d7438a106289b7477d31a967ab91");
        assertThat(SuiApproval.batchDigest(DOMAIN, batch())).isEqualTo(Hashing.keccak256(HexCodec.concat(
            new byte[] {0x19, 0x01}, expectedDomain.bytes(), Eip712.batchStructHash(batch()).bytes()
        )));
        assertThat(SuiApproval.statusDigest(DOMAIN, status())).isEqualTo(Hashing.keccak256(HexCodec.concat(
            new byte[] {0x19, 0x01}, expectedDomain.bytes(), Eip712.statusStructHash(status()).bytes()
        )));

        Eip712.Domain legacyDomain = new Eip712.Domain(
            BigInteger.valueOf(1001), EthereumAddress.fromHex("0x" + "11".repeat(20))
        );
        assertThat(Eip712.batchDigest(legacyDomain, batch()).hex())
            .isEqualTo("0x54e8c1c27ee393ec23765d36c0044b01541c0ab9bb801cfc2cd56f30443808e5");
        assertThat(SuiApproval.batchDigest(DOMAIN, batch())).isNotEqualTo(Eip712.batchDigest(legacyDomain, batch()));
        assertThat(SuiApproval.statusDigest(DOMAIN, status())).isNotEqualTo(Eip712.statusDigest(legacyDomain, status()));
        assertThat(SuiApproval.batchDigest(DOMAIN, batch())).isNotEqualTo(SuiApproval.statusDigest(DOMAIN, status()));
    }

    @Test
    void neitherBatchNorStatusSignaturesReplayAcrossNetworkPackageOrRegistry() {
        Hash32 batchDigest = SuiApproval.batchDigest(DOMAIN, batch());
        Hash32 statusDigest = SuiApproval.statusDigest(DOMAIN, status());
        Signature65 batchSignature = Eip712.signDigest(batchDigest, TEST_KEY);
        Signature65 statusSignature = Eip712.signDigest(statusDigest, TEST_KEY);
        assertThat(Eip712.recoverSigner(batchDigest, batchSignature)).isEqualTo(SIGNER);
        assertThat(Eip712.recoverSigner(statusDigest, statusSignature)).isEqualTo(SIGNER);
        for (SuiApproval.Domain other : List.of(
            new SuiApproval.Domain("4c78adad", PACKAGE_ID, REGISTRY_ID),
            new SuiApproval.Domain(DOMAIN.chainIdentifier(), "0x" + "33".repeat(32), REGISTRY_ID),
            new SuiApproval.Domain(DOMAIN.chainIdentifier(), PACKAGE_ID, "0x" + "33".repeat(32)),
            new SuiApproval.Domain(DOMAIN.chainIdentifier(), REGISTRY_ID, PACKAGE_ID)
        )) {
            assertThat(SuiApproval.domainHash(other)).isNotEqualTo(SuiApproval.domainHash(DOMAIN));
            assertThat(Eip712.recoverSigner(SuiApproval.batchDigest(other, batch()), batchSignature)).isNotEqualTo(SIGNER);
            assertThat(Eip712.recoverSigner(SuiApproval.statusDigest(other, status()), statusSignature)).isNotEqualTo(SIGNER);
        }
        assertThat(Eip712.recoverSigner(statusDigest, batchSignature)).isNotEqualTo(SIGNER);
        assertThat(Eip712.recoverSigner(batchDigest, statusSignature)).isNotEqualTo(SIGNER);
    }

    @Test
    void batchSignatureCommitsToEveryMessageField() throws Exception {
        ObjectNode message = (ObjectNode) OBJECT_MAPPER.readTree(SuiApproval.batchPayloadJson(DOMAIN, batch())).get("message");
        Hash32 digest = SuiApproval.batchDigest(DOMAIN, batch());
        Signature65 signature = Eip712.signDigest(digest, TEST_KEY);
        assertThat(message.size()).isEqualTo(9);
        for (String field : message.propertyStream().map(java.util.Map.Entry::getKey).toList()) {
            ObjectNode changed = mutatedMessage(message, field);
            Hash32 changedDigest = SuiApproval.batchDigest(DOMAIN, batch(changed));
            assertThat(changedDigest).as(field).isNotEqualTo(digest);
            assertThat(Eip712.recoverSigner(changedDigest, signature)).as(field).isNotEqualTo(SIGNER);
        }
    }

    @Test
    void statusSignatureCommitsToEveryMessageField() throws Exception {
        ObjectNode message = (ObjectNode) OBJECT_MAPPER.readTree(SuiApproval.statusPayloadJson(DOMAIN, status())).get("message");
        Hash32 digest = SuiApproval.statusDigest(DOMAIN, status());
        Signature65 signature = Eip712.signDigest(digest, TEST_KEY);
        assertThat(message.size()).isEqualTo(8);
        for (String field : message.propertyStream().map(java.util.Map.Entry::getKey).toList()) {
            ObjectNode changed = mutatedMessage(message, field);
            Hash32 changedDigest = SuiApproval.statusDigest(DOMAIN, status(changed));
            assertThat(changedDigest).as(field).isNotEqualTo(digest);
            assertThat(Eip712.recoverSigner(changedDigest, signature)).as(field).isNotEqualTo(SIGNER);
        }
    }

    @Test
    void payloadDeclaresItsOwnSchemeAndPreservesUint64ValuesAsDecimalStrings() throws Exception {
        Eip712.BatchApproval maxBatch = new Eip712.BatchApproval(
            batch().issuerId(), batch().batchIdHash(), batch().merkleRoot(), batch().schemaVersionHash(),
            BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE), BigInteger.valueOf(65535), UINT64_MAX, UINT64_MAX, UINT64_MAX
        );
        Eip712.StatusApproval maxStatus = new Eip712.StatusApproval(
            status().issuerId(), status().credentialIdHash(), Eip712.StatusAction.REVOKE, Hash32.ZERO,
            UINT64_MAX, UINT64_MAX, UINT64_MAX, UINT64_MAX
        );
        JsonNode batchPayload = OBJECT_MAPPER.readTree(SuiApproval.batchPayloadJson(DOMAIN, maxBatch));
        JsonNode statusPayload = OBJECT_MAPPER.readTree(SuiApproval.statusPayloadJson(DOMAIN, maxStatus));
        for (JsonNode payload : List.of(batchPayload, statusPayload)) {
            assertThat(payload.size()).isEqualTo(6);
            assertThat(payload.get("scheme").asText()).isEqualTo("TREKKEY_SUI_APPROVAL_V1");
            assertThat(payload.get("signatureScheme").asText()).isEqualTo("secp256k1-recoverable-low-s");
            assertThat(payload.get("domain").size()).isEqualTo(3);
            assertThat(payload.at("/domain/chainIdentifier").asText()).isEqualTo(DOMAIN.chainIdentifier());
            assertThat(payload.at("/domain/packageId").asText()).isEqualTo(PACKAGE_ID);
            assertThat(payload.at("/domain/registryId").asText()).isEqualTo(REGISTRY_ID);
            assertThat(payload.toString()).doesNotContain("EIP712Domain", "verifyingContract", "chainId\"", "\"types\"");
            assertThat(payload.get("message").valueStream().allMatch(JsonNode::isTextual)).isTrue();
            assertThat(payload.at("/message/issuerKeyVersion").asText()).isEqualTo(UINT64_MAX.toString());
            assertThat(payload.at("/message/approvalNonce").asText()).isEqualTo(UINT64_MAX.toString());
            assertThat(payload.at("/message/deadline").asText()).isEqualTo(UINT64_MAX.toString());
        }
        assertThat(batchPayload.get("primaryType").asText()).isEqualTo("BatchApproval");
        assertThat(batchPayload.get("digestHex").asText()).isEqualTo(SuiApproval.batchDigest(DOMAIN, maxBatch).hex());
        assertThat(batchPayload.at("/message/leafCount").asText()).isEqualTo("4294967295");
        assertThat(batchPayload.at("/message/treeVersion").asText()).isEqualTo("65535");
        assertThat(statusPayload.get("primaryType").asText()).isEqualTo("StatusApproval");
        assertThat(statusPayload.get("digestHex").asText()).isEqualTo(SuiApproval.statusDigest(DOMAIN, maxStatus).hex());
        assertThat(statusPayload.at("/message/action").asText()).isEqualTo("0");
        assertThat(statusPayload.at("/message/replacementCredentialIdHash").asText()).isEqualTo(Hash32.ZERO.hex());
        assertThat(statusPayload.at("/message/effectiveAt").asText()).isEqualTo(UINT64_MAX.toString());
    }

    @Test
    void existingSignature65RulesRemainLowSAndRecoverableWithoutPersonalSignPrefix() {
        Hash32 digest = SuiApproval.batchDigest(DOMAIN, batch());
        Signature65 signature = Eip712.signDigest(digest, TEST_KEY);
        assertThat(signature.bytes()).hasSize(65);
        assertThat(signature.hex()).matches("0x[0-9a-f]{130}");
        assertThat(Eip712.recoverSigner(digest, signature)).isEqualTo(SIGNER);
        byte[] compact = signature.bytes();
        compact[64] -= 27;
        assertThat(Signature65.of(compact).hex()).isEqualTo(signature.hex());
        byte[] highS = signature.bytes();
        byte[] firstHighS = HexCodec.decode(
            "0x7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a1", 32, "high-s"
        );
        System.arraycopy(firstHighS, 0, highS, 32, 32);
        assertThatThrownBy(() -> Signature65.of(highS))
            .isInstanceOf(CryptoValidationException.class).hasMessageContaining("low-s");
        byte[] invalidRecovery = signature.bytes();
        invalidRecovery[64] = 29;
        assertThatThrownBy(() -> Signature65.of(invalidRecovery)).isInstanceOf(CryptoValidationException.class);
        Hash32 personalSignDigest = Hashing.keccak256(HexCodec.concat(
            "\u0019Ethereum Signed Message:\n32".getBytes(StandardCharsets.UTF_8), digest.bytes()
        ));
        assertThat(Eip712.recoverSigner(personalSignDigest, signature)).isNotEqualTo(SIGNER);
    }

    private static ObjectNode mutatedMessage(ObjectNode message, String field) {
        ObjectNode changed = message.deepCopy();
        String value = message.get(field).asText();
        changed.put(field, value.startsWith("0x") ? "0x" + "ab".repeat(32)
            : field.equals("action") ? "0" : new BigInteger(value).add(BigInteger.ONE).toString());
        return changed;
    }

    private static Eip712.BatchApproval batch() {
        return new Eip712.BatchApproval(
            Hashing.keccak256Utf8("issuer"), Hashing.keccak256Utf8("batch"), Hashing.keccak256Utf8("root"),
            Hashing.keccak256Utf8("schema"), BigInteger.valueOf(3), BigInteger.ONE, BigInteger.TWO,
            BigInteger.valueOf(7), BigInteger.valueOf(1_700_000_000L)
        );
    }

    private static Eip712.StatusApproval status() {
        return new Eip712.StatusApproval(
            Hashing.keccak256Utf8("issuer"), Hashing.keccak256Utf8("credential"), Eip712.StatusAction.SUPERSEDE,
            Hashing.keccak256Utf8("replacement"), BigInteger.valueOf(1_700_000_000L), BigInteger.ONE,
            BigInteger.TWO, BigInteger.valueOf(1_800_000_000L)
        );
    }

    private static Eip712.BatchApproval batch(JsonNode message) {
        return new Eip712.BatchApproval(
            hash(message, "issuerId"), hash(message, "batchIdHash"), hash(message, "merkleRoot"),
            hash(message, "schemaVersionHash"), number(message, "leafCount"), number(message, "treeVersion"),
            number(message, "issuerKeyVersion"), number(message, "approvalNonce"), number(message, "deadline")
        );
    }

    private static Eip712.StatusApproval status(JsonNode message) {
        return new Eip712.StatusApproval(
            hash(message, "issuerId"), hash(message, "credentialIdHash"),
            switch (message.get("action").asText()) {
                case "0" -> Eip712.StatusAction.REVOKE;
                case "1" -> Eip712.StatusAction.SUPERSEDE;
                default -> throw new IllegalArgumentException("unknown fixture status action");
            },
            hash(message, "replacementCredentialIdHash"), number(message, "effectiveAt"),
            number(message, "issuerKeyVersion"), number(message, "approvalNonce"), number(message, "deadline")
        );
    }

    private static Hash32 hash(JsonNode message, String field) {
        return Hash32.fromHex(message.get(field).asText());
    }

    private static BigInteger number(JsonNode message, String field) {
        return new BigInteger(message.get(field).asText());
    }
}
