package com.api.trekkey.domain.credential.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

class CredentialCryptoTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String HASH_257 = "0x25706963ced8aa7c1d61a27269aafba6a65f35dbaaf7eb38d9c2e3c1fe84aeff";
    private static final String HASH_683 = "0x6837c46a55233e25a80a7a6b6370cf583b56f00298a83b4b2cc1673dffba5830";
    private static final String HASH_6FC = "0x6fce77fce644955328fdccc6aa6a75409f34d5cf13eab02ed162673b754b53ee";
    private static final String HASH_7BA = "0x7ba17c6324a980bec346a8e0c3924912727797fa0481428476ee4616ee982d9d";
    private static final String HASH_942 = "0x942e86b0c4205dc3135994659f1ef99e1feef1f76a2feb2eb8ed03017f71b8a1";
    private static final String HASH_B60 = "0xb60b794b1bb121fc693f29deb83f6f595e262cdf9f1142884dd44399af4d2cb8";
    private static final String HASH_EC0 = "0xec0c30db1312c3571afacc92e3cb184c66d5890d98c5577217b98b2161c0f699";

    @Test
    void reproducesTheCommittedOpenZeppelinFixtureByteForByte() throws Exception {
        JsonNode fixture = OBJECT_MAPPER.readTree(Files.readString(
            Path.of("contracts/test/fixtures/merkle-v1.json"), StandardCharsets.UTF_8
        ));

        assertThat(CredentialLeaf.LEAF_DOMAIN.hex()).isEqualTo(fixture.get("leafDomain").asText());
        for (JsonNode fixtureCase : fixture.withArray("cases")) {
            List<CredentialLeaf> leaves = fixtureCase.withArray("leaves").valueStream()
                .map(this::fixtureLeaf)
                .toList();
            StandardMerkleTree tree = StandardMerkleTree.fromLeaves(leaves);

            assertThat(tree.root().hex()).isEqualTo(fixtureCase.get("root").asText());
            assertThat(tree.leaves()).extracting(Hash32::hex)
                .containsExactlyElementsOf(fixtureCase.withArray("sortedLeaves").valueStream().map(JsonNode::asText).toList());

            for (JsonNode fixtureLeaf : fixtureCase.withArray("leaves")) {
                CredentialLeaf leaf = fixtureLeaf(fixtureLeaf);
                Hash32 leafHash = leaf.hash();
                assertThat(HexCodec.encode(leaf.abiEncodedValue())).isEqualTo(fixtureLeaf.get("encodedLeafValue").asText());
                assertThat(leafHash.hex()).isEqualTo(fixtureLeaf.get("leafHash").asText());
                assertThat(tree.leafIndex(leafHash)).isEqualTo(fixtureLeaf.get("leafIndex").asInt());
                assertThat(tree.proofForLeaf(leafHash)).extracting(Hash32::hex)
                    .containsExactlyElementsOf(fixtureLeaf.withArray("proof").valueStream().map(JsonNode::asText).toList());
                assertThat(StandardMerkleTree.verify(tree.root(), leafHash, tree.proofForLeaf(leafHash))).isTrue();
            }
        }
    }

    @Test
    void followsOpenZeppelinCompleteTreeReferencesBeyondThreeLeaves() {
        Map<Integer, String> roots = Map.of(
            4, "0xf1445693cefa6e763edd00b9a4d61a6071bff6197e8d23438869f933c32f9e7d",
            5, "0xb380c7ab192b977ab42cf510f381c79d25f2e10d3f3f9997676a1a0f31e06974",
            6, "0x8f48c356a4cf15bcfe236c44d0b6b777ddff6825025e87a0fb9f59e63731b129",
            7, "0x6341957af3ec38281c62843dd3954f493fbbb1d3f35e1cb0e11f9c223857e03d"
        );
        for (int count = 4; count <= 7; count++) {
            StandardMerkleTree tree = StandardMerkleTree.fromLeafHashes(referenceLeaves().subList(0, count));
            assertThat(tree.root().hex()).isEqualTo(roots.get(count));
            for (Hash32 leaf : tree.leaves()) {
                assertThat(StandardMerkleTree.verify(tree.root(), leaf, tree.proofForLeaf(leaf))).isTrue();
            }
        }

        StandardMerkleTree fiveLeafTree = StandardMerkleTree.fromLeafHashes(referenceLeaves().subList(0, 5));
        assertThat(fiveLeafTree.proofForLeaf(Hash32.fromHex(HASH_B60))).extracting(Hash32::hex).containsExactly(
            "0x05559181a8d9df6c5fa2b88a149e5ea377981a57ab0b43d880e9d8890adfd503",
            "0x5fbf4d850a42cca4e09c28415070c37542dacc35e766f99581f4bfaf5256c1f7"
        );
        StandardMerkleTree sevenLeafTree = StandardMerkleTree.fromLeafHashes(referenceLeaves());
        assertThat(sevenLeafTree.proofForLeaf(Hash32.fromHex(HASH_B60))).extracting(Hash32::hex).containsExactly(
            HASH_942,
            "0xd6e05ae21681f6d314b74bd2298f268e377e091d92935bb3e93618b5b12affe2",
            "0x2bc4ab98072f38d582c1da1a643bf9879d781568bf0233cdc9cf3190474de8bc"
        );
    }

    @Test
    void rejectsDuplicateLeavesAndTamperedProofs() {
        Hash32 first = Hashing.keccak256Utf8("leaf:first");
        Hash32 second = Hashing.keccak256Utf8("leaf:second");
        StandardMerkleTree tree = StandardMerkleTree.fromLeafHashes(List.of(first, second));

        assertThat(StandardMerkleTree.verify(tree.root(), first, List.of(Hashing.keccak256Utf8("tampered")))).isFalse();
        assertThatThrownBy(() -> StandardMerkleTree.fromLeafHashes(List.of(first, first)))
            .isInstanceOf(CryptoValidationException.class);
    }

    @Test
    void validatesFixedWidthHexValuesBeforeTheyReachTheProtocol() {
        assertThatThrownBy(() -> Hash32.fromHex("0x01"))
            .isInstanceOf(CryptoValidationException.class)
            .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> EthereumAddress.fromHex("1111111111111111111111111111111111111111"))
            .isInstanceOf(CryptoValidationException.class)
            .hasMessageContaining("0x-prefixed");
        assertThatThrownBy(() -> Signature65.fromHex("0x" + "00".repeat(64)))
            .isInstanceOf(CryptoValidationException.class)
            .hasMessageContaining("65 bytes");
    }

    @Test
    void normalizesCompactRecoveryIdsAndRejectsNonCanonicalSignatures() {
        Signature65 canonical = Signature65.fromHex(
            "0x022acb0158417011f4d6942483234562d708c152097c07abf66240fd4a4fc1a00b851c50f9c4b680c9dc48ba69c3404369c9e8be79eda86fbfc8146e7b65c1d01c"
        );
        byte[] compact = canonical.bytes();
        compact[64] = 1;

        Signature65 normalized = Signature65.of(compact);

        assertThat(normalized.hex()).isEqualTo(canonical.hex());
        assertThat(normalized.bytes()[64]).isEqualTo((byte) 28);

        byte[] highS = canonical.bytes();
        byte[] firstHighS = new BigInteger(
            "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A1", 16
        ).toByteArray();
        System.arraycopy(firstHighS, 0, highS, 32, 32);
        assertThatThrownBy(() -> Signature65.of(highS))
            .isInstanceOf(CryptoValidationException.class)
            .hasMessageContaining("low-s");

        byte[] zeroR = canonical.bytes();
        java.util.Arrays.fill(zeroR, 0, 32, (byte) 0);
        assertThatThrownBy(() -> Signature65.of(zeroR))
            .isInstanceOf(CryptoValidationException.class)
            .hasMessageContaining("scalar range");
    }

    @Test
    void canonicalizesNfcAndRejectsCollisionsFloatsAndKeyOrderAmbiguity() {
        byte[] decomposed = CanonicalJson.canonicalize("{\"z\":1,\"e\\u0301\":\"e\\u0301\",\"a\":2}");
        byte[] composed = CanonicalJson.canonicalize("{\"a\":2,\"é\":\"é\",\"z\":1}");

        assertThat(decomposed).isEqualTo(composed);
        assertThat(CanonicalJson.utf8(decomposed)).isEqualTo("{\"a\":2,\"z\":1,\"é\":\"é\"}");
        assertThatThrownBy(() -> CanonicalJson.canonicalize("{\"e\\u0301\":1,\"é\":2}"))
            .isInstanceOf(CryptoValidationException.class)
            .hasMessageContaining("after NFC normalization");
        assertThatThrownBy(() -> CanonicalJson.canonicalize("{\"score\":1.0}"))
            .isInstanceOf(CryptoValidationException.class)
            .hasMessageContaining("floating-point");
    }

    @Test
    void makesManifestAndSourceFingerprintIndependentOfInputOrder() throws Exception {
        Hash32 one = Hash32.fromHex("0x0101010101010101010101010101010101010101010101010101010101010101");
        Hash32 two = Hash32.fromHex("0x0202020202020202020202020202020202020202020202020202020202020202");
        FileManifest.Result manifest = FileManifest.build(List.of(
            new FileManifest.Entry("b.txt", "text/plain", 12, two),
            new FileManifest.Entry("a.txt", "text/plain", 4, one)
        ));
        assertThat(CanonicalJson.utf8(manifest.canonicalBytes())).isEqualTo(
            "{\"files\":[{\"contentType\":\"text/plain\",\"originalName\":\"a.txt\",\"sha256Hex\":\""
                + one.hex() + "\",\"sizeBytes\":4},{\"contentType\":\"text/plain\",\"originalName\":\"b.txt\",\"sha256Hex\":\""
                + two.hex() + "\",\"sizeBytes\":12}],\"manifestVersion\":1}"
        );

        SourceFingerprint.SubjectSnapshot first = new SourceFingerprint.SubjectSnapshot(
            "student-2", "MEMBER", OBJECT_MAPPER.readTree("{\"major\":\"Software\",\"displayName\":\"Kim\"}")
        );
        SourceFingerprint.SubjectSnapshot second = new SourceFingerprint.SubjectSnapshot(
            "student-1", "LEADER", OBJECT_MAPPER.readTree("{\"displayName\":\"Lee\",\"major\":\"Design\"}")
        );
        JsonNode sourceSnapshot = OBJECT_MAPPER.readTree("{\"award\":\"Grand Prize\",\"contestPublicId\":\"contest-1\"}");
        SourceFingerprint.Input ordered = new SourceFingerprint.Input(
            "org-1", "AWARD", "TEAM_AWARD", "award-1", sourceSnapshot, List.of(first, second), "trekkey:award:v1"
        );
        SourceFingerprint.Input reversed = new SourceFingerprint.Input(
            "org-1", "AWARD", "TEAM_AWARD", "award-1", sourceSnapshot, List.of(second, first), "trekkey:award:v1"
        );
        SourceFingerprint.Result orderedResult = SourceFingerprint.build(ordered);
        SourceFingerprint.Result reversedResult = SourceFingerprint.build(reversed);

        assertThat(orderedResult.sourceSnapshotHash()).isEqualTo(reversedResult.sourceSnapshotHash());
        assertThat(orderedResult.subjectSetHash()).isEqualTo(reversedResult.subjectSetHash());
        assertThat(orderedResult.fingerprintHash()).isEqualTo(reversedResult.fingerprintHash());
        assertThat(CanonicalJson.utf8(orderedResult.fingerprintCanonicalBytes())).doesNotContain("sourceVersion");
    }

    @Test
    void derivesApplicationIdentifiersWithAbiDomainSeparation() {
        Hash32 issuerId = Hashing.issuerId("organization:demo-1");

        assertThat(issuerId.hex()).isEqualTo("0xa37049d238083ddd27abf1214bb6b3d5dd3575a9b7d9759c07e67a99ac92bd5b");
        assertThat(Hashing.credentialId(issuerId, "credential:demo-1").hex())
            .isEqualTo("0x053e1916609a8156cb89613d51fff1b285c928a5c94e75ab1e7ded259cbab7a7");
        assertThat(Hashing.batchId(issuerId, "batch:demo-1").hex())
            .isEqualTo("0x72ea4d6c7b6859e7359ac876b89892af8a15026bac35ee4148af585327b2b7e7");
        assertThat(Hashing.schemaVersion("trekkey:credential:award:v1").hex())
            .isEqualTo("0x82b2fb3f9d514415533309fdb48c551ea9f317ac74e077f153d39b57f92afea0");
        assertThat(issuerId).isNotEqualTo(Hashing.keccak256Utf8("organization:demo-1"));
    }

    @Test
    void computesEip712DigestAndRecoversOnlyTheCorrectSigner() {
        Eip712.Domain domain = new Eip712.Domain(
            BigInteger.valueOf(1001), EthereumAddress.fromHex("0x1111111111111111111111111111111111111111")
        );
        Eip712.BatchApproval approval = new Eip712.BatchApproval(
            Hashing.keccak256Utf8("issuer"),
            Hashing.keccak256Utf8("batch"),
            Hashing.keccak256Utf8("root"),
            Hashing.keccak256Utf8("schema"),
            BigInteger.valueOf(3), BigInteger.ONE, BigInteger.valueOf(2), BigInteger.valueOf(7), BigInteger.valueOf(1_700_000_000L)
        );
        Hash32 digest = Eip712.batchDigest(domain, approval);
        assertThat(Eip712.batchStructHash(approval).hex())
            .isEqualTo("0x99983d30fc6a20f3231962cec593c737bc07d7438a106289b7477d31a967ab91");
        assertThat(digest.hex()).isEqualTo("0x54e8c1c27ee393ec23765d36c0044b01541c0ab9bb801cfc2cd56f30443808e5");
        Signature65 ethersSignature = Signature65.fromHex(
            "0x022acb0158417011f4d6942483234562d708c152097c07abf66240fd4a4fc1a00b851c50f9c4b680c9dc48ba69c3404369c9e8be79eda86fbfc8146e7b65c1d01c"
        );
        EthereumAddress signer = EthereumAddress.fromHex("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266");
        assertThat(Eip712.recoverSigner(digest, ethersSignature)).isEqualTo(signer);
        assertThat(Eip712.recoverSigner(
            Eip712.batchDigest(new Eip712.Domain(BigInteger.valueOf(1002), domain.verifyingContract()), approval), ethersSignature
        )).isNotEqualTo(signer);
        assertThat(Eip712.recoverSigner(
            Eip712.batchDigest(new Eip712.Domain(BigInteger.valueOf(1001), EthereumAddress.fromHex("0x2222222222222222222222222222222222222222")), approval), ethersSignature
        )).isNotEqualTo(signer);

        Signature65 locallySigned = Eip712.signDigest(digest, ECKeyPair.create(new BigInteger(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80", 16
        )));
        assertThat(Eip712.recoverSigner(digest, locallySigned)).isEqualTo(signer);
        assertThat(Eip712.batchTypedDataJson(domain, approval)).contains("\"primaryType\":\"BatchApproval\"");
        assertThatThrownBy(() -> new Eip712.BatchApproval(
            approval.issuerId(), approval.batchIdHash(), approval.merkleRoot(), approval.schemaVersionHash(),
            BigInteger.ONE.shiftLeft(32), BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        )).isInstanceOf(CryptoValidationException.class);
    }

    @Test
    void encodesStatusApprovalWithTheContractActionValuesAndUintBounds() {
        Eip712.Domain domain = new Eip712.Domain(
            BigInteger.valueOf(1001), EthereumAddress.fromHex("0x1111111111111111111111111111111111111111")
        );
        Eip712.StatusApproval approval = new Eip712.StatusApproval(
            Hashing.keccak256Utf8("issuer"),
            Hashing.keccak256Utf8("credential"),
            Eip712.StatusAction.SUPERSEDE,
            Hashing.keccak256Utf8("replacement"),
            BigInteger.valueOf(1_700_000_000L),
            BigInteger.ONE,
            BigInteger.TWO,
            BigInteger.valueOf(1_800_000_000L)
        );

        assertThat(Eip712.statusTypedDataJson(domain, approval)).contains("\"action\":\"1\"");
        assertThat(Eip712.statusDigest(domain, approval)).isNotEqualTo(Eip712.batchDigest(domain,
            new Eip712.BatchApproval(
                approval.issuerId(), Hashing.keccak256Utf8("batch"), Hashing.keccak256Utf8("root"),
                Hashing.keccak256Utf8("schema"), BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.TWO,
                BigInteger.valueOf(1_800_000_000L)
            )
        ));
        assertThatThrownBy(() -> new Eip712.StatusApproval(
            approval.issuerId(), approval.credentialIdHash(), Eip712.StatusAction.REVOKE, Hash32.ZERO,
            BigInteger.ONE.shiftLeft(64), BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        )).isInstanceOf(CryptoValidationException.class);
    }

    private CredentialLeaf fixtureLeaf(JsonNode node) {
        return new CredentialLeaf(
            Hash32.fromHex(node.get("issuerId").asText()),
            Hash32.fromHex(node.get("credentialIdHash").asText()),
            Hash32.fromHex(node.get("schemaVersionHash").asText()),
            Hash32.fromHex(node.get("contentHash").asText()),
            Hash32.fromHex(node.get("fileManifestHash").asText())
        );
    }

    private static List<Hash32> referenceLeaves() {
        return List.of(HASH_B60, HASH_6FC, HASH_942, HASH_257, HASH_7BA, HASH_EC0, HASH_683)
            .stream().map(Hash32::fromHex).toList();
    }
}
