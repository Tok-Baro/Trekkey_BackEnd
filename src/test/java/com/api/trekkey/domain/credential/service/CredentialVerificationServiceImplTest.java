package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.CanonicalJson;
import com.api.trekkey.domain.credential.crypto.CredentialLeaf;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.FileManifest;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncBatchItem;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.crypto.SuiDigest;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationStatus;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.infrastructure.blockchain.BlockchainVerificationRouter;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import com.api.trekkey.domain.credential.service.support.CredentialPayloadFactory;
import com.api.trekkey.domain.credential.service.support.UtcTime;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CredentialVerificationServiceImplTest {

    private static final String CREDENTIAL_PUBLIC_ID = "credential-public-1";
    private static final String ORGANIZATION_PUBLIC_ID = "organization-public-1";
    private static final Instant ISSUED_AT = Instant.parse("2026-07-24T01:05:00.123456789Z");

    @Mock private AncCredentialRepository credentialRepository;
    @Mock private AncBatchItemRepository batchItemRepository;
    @Mock private AncBatchRepository batchRepository;
    @Mock private AncIssuerKeyRepository issuerKeyRepository;
    @Mock private AncChainTransactionRepository chainTransactionRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private BlockchainAnchorPort blockchainAnchorPort;

    private CredentialVerificationServiceImpl service;
    private BlockchainProperties properties;
    private Organization organization;
    private AncCredential credential;

    @BeforeEach
    void setUp() throws Exception {
        organization = organization();
        credential = credential();
        properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.READ_ONLY);
        properties.setContractAddress("0x1111111111111111111111111111111111111111");
        service = new CredentialVerificationServiceImpl(
                credentialRepository,
                batchItemRepository,
                batchRepository,
                issuerKeyRepository,
                chainTransactionRepository,
                organizationRepository,
                new BlockchainVerificationRouter(properties, blockchainAnchorPort),
                properties,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-24T02:00:00Z"), ZoneOffset.UTC));

        given(credentialRepository.findByPublicId(CREDENTIAL_PUBLIC_ID)).willReturn(Optional.of(credential));
        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        lenient().when(batchItemRepository.findByCredentialId(10L)).thenReturn(Optional.empty());
    }

    @Test
    void returnsOnlyClaimsParsedFromTheCanonicalCredential() {
        byte[] originalCanonical = credential.getCanonicalBytes().clone();
        byte[] originalHash = credential.getContentHash().clone();
        ReflectionTestUtils.setField(organization, "name", "현재 학교 이름");

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.PENDING);
        assertThat(result.credentialNo()).isEqualTo("AWARD-2026-001");
        assertThat(result.issuerName()).isEqualTo("발급 당시 학교 이름");
        assertThat(result.publicDetails().contestTitle()).isEqualTo("2026 캡스톤 경진대회");
        assertThat(result.publicDetails().submissionTitle()).isEqualTo("트레키 작품");
        assertThat(result.publicDetails().prize()).isEqualTo("대상");
        assertThat(result.publicDetails().awardRankNo()).isEqualTo(1);
        assertThat(result.publicSubjects())
                .extracting(CredentialVerificationView.PublicSubject::displayName)
                .containsExactly("공개 학생");
        assertThat(result.publicSubjects()).extracting(CredentialVerificationView.PublicSubject::subjectRef)
                .containsExactly("public-subject:" + CREDENTIAL_PUBLIC_ID + ":0");
        assertThat(credential.getCanonicalBytes()).containsExactly(originalCanonical);
        assertThat(credential.getContentHash()).containsExactly(originalHash);
        assertThat(result.evidence().credentialClaimsMatch()).isTrue();
        assertThat(result.evidence().issuerId()).startsWith("0x");
        assertThat(result.evidence().credentialIdHash()).startsWith("0x");
        assertThat(result.evidence().contentHash()).startsWith("0x");
        assertThat(result.evidence().fileManifestHash()).startsWith("0x");
    }

    @Test
    void rejectsAStoredTimestampOutsideBothPermittedMicrosecondRepresentations() {
        ReflectionTestUtils.setField(
                credential,
                "issuedAt",
                credential.getIssuedAt().plusNanos(2_000));

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.TAMPERED);
        assertThat(result.evidence().credentialClaimsMatch()).isFalse();
    }

    @Test
    void acceptsMySqlRoundedMetadataWithoutMutatingCanonicalBytesOrHashes() {
        byte[] originalCanonical = credential.getCanonicalBytes();
        byte[] originalHash = credential.getContentHash();
        String originalJson = credential.getPayloadJson();
        ReflectionTestUtils.setField(credential, "issuedAt", credential.getIssuedAt().plusNanos(1_000));
        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);
        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.PENDING);
        assertThat(result.evidence().credentialClaimsMatch()).isTrue();
        assertThat(result.evidence().canonicalPayloadMatches()).isTrue();
        assertThat(result.evidence().contentHashMatches()).isTrue();
        assertThat(credential.getCanonicalBytes()).containsExactly(originalCanonical);
        assertThat(credential.getContentHash()).containsExactly(originalHash);
        assertThat(credential.getPayloadJson()).isEqualTo(originalJson);
    }

    @Test
    void marksDuplicatedDatabaseClaimsAsTamperedAndDoesNotDisplayThem() {
        ReflectionTestUtils.setField(credential, "credentialNo", "DB-TAMPERED");

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.TAMPERED);
        assertThat(result.credentialNo()).isNull();
        assertThat(result.publicSubjects()).isEmpty();
        assertThat(result.evidence().credentialClaimsMatch()).isFalse();
    }

    @Test
    void marksTamperedDatabaseSchemaAsTamperedInsteadOfUnsupported() {
        ReflectionTestUtils.setField(credential, "schemaProfileId", "tampered:schema:v9");

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.TAMPERED);
        assertThat(result.evidence().credentialClaimsMatch()).isFalse();
    }

    @Test
    void marksMissingBatchMembershipAsTamperedAfterCredentialWasBatched() {
        credential.markBatched();

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.TAMPERED);
        assertThat(result.evidence().merkleProofMatches()).isFalse();
    }

    @Test
    void returnsRevokedOnlyAfterBatchAndStatusIssuerEvidenceAreValid() {
        AnchoredEvidence evidence = anchoredEvidence();
        long effectiveAt = ISSUED_AT.plusSeconds(120).getEpochSecond();
        long recordedAt = effectiveAt + 10;
        stubValidChainEvidence(
                evidence,
                new BlockchainAnchorPort.OnChainCredentialStatus(
                        BlockchainAnchorPort.CredentialChainState.REVOKED,
                        effectiveAt,
                        recordedAt,
                        evidence.issuerKey().getKeyVersion(),
                        Hash32.ZERO),
                0);

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.REVOKED);
        assertThat(result.evidence().merkleProofMatches()).isTrue();
    }

    @Test
    void rejectsAStatusRecordedAfterTheIssuerKeyCompromiseTime() {
        AnchoredEvidence evidence = anchoredEvidence();
        long effectiveAt = ISSUED_AT.plusSeconds(120).getEpochSecond();
        long recordedAt = effectiveAt + 10;
        stubValidChainEvidence(
                evidence,
                new BlockchainAnchorPort.OnChainCredentialStatus(
                        BlockchainAnchorPort.CredentialChainState.REVOKED,
                        effectiveAt,
                        recordedAt,
                        evidence.issuerKey().getKeyVersion(),
                        Hash32.ZERO),
                recordedAt - 1);

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.ISSUER_INVALID);
    }

    @Test
    void marksAnAnchorAtOrAfterABackdatedCompromiseAsIssuerInvalid() {
        AnchoredEvidence evidence = anchoredEvidence();
        stubValidChainEvidence(
                evidence,
                new BlockchainAnchorPort.OnChainCredentialStatus(
                        BlockchainAnchorPort.CredentialChainState.NONE,
                        0,
                        0,
                        0,
                        Hash32.ZERO),
                ISSUED_AT.plusSeconds(20).getEpochSecond());

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.ISSUER_INVALID);
    }

    @Test
    void distinguishesRetryableRpcOutagesFromPermanentBlockchainConfigurationErrors() {
        AnchoredEvidence evidence = anchoredEvidence();
        Hash32 batchIdHash = Hash32.of(evidence.batch().getBatchIdHash());
        given(blockchainAnchorPort.getBatch(batchIdHash))
                .willThrow(new BlockchainGatewayException(
                        "BLOCKCHAIN_RPC_UNAVAILABLE", true, "temporary outage"))
                .willThrow(new BlockchainGatewayException(
                        "BLOCKCHAIN_CHAIN_ID_MISMATCH", false, "wrong chain"));

        CredentialVerificationView outage = service.verify(CREDENTIAL_PUBLIC_ID);
        CredentialVerificationView configurationError = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(outage.verificationStatus()).isEqualTo(CredentialVerificationStatus.RPC_UNAVAILABLE);
        assertThat(configurationError.verificationStatus())
                .isEqualTo(CredentialVerificationStatus.BLOCKCHAIN_CONFIGURATION_ERROR);
    }

    @Test
    void neverGeneratesAnIssuerIdInsideTheReadOnlyVerificationPath() {
        ReflectionTestUtils.setField(organization, "publicId", null);

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.TAMPERED);
        assertThat(organization.getPublicId()).isNull();
        assertThat(result.evidence().issuerId()).isNull();
    }

    private Organization organization() {
        Organization value = org.springframework.beans.BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(value, "id", 1L);
        ReflectionTestUtils.setField(value, "publicId", ORGANIZATION_PUBLIC_ID);
        ReflectionTestUtils.setField(value, "name", "현재 학교 이름");
        return value;
    }

    @Test
    void suiVerificationKeepsExistingClaimsAndMerkleContractWithHonestNativeCoordinates() {
        AnchoredEvidence evidence = anchoredEvidence();
        configureSui();
        ReflectionTestUtils.setField(evidence.batch(), "chainContext", properties.chainContext());
        ReflectionTestUtils.setField(evidence.issuerKey(), "chainContext", properties.chainContext());
        AncChainTransaction tx = suiTransaction(evidence);
        stubValidChainEvidence(evidence, new BlockchainAnchorPort.OnChainCredentialStatus(
                BlockchainAnchorPort.CredentialChainState.NONE, 0, 0, 0, Hash32.ZERO), 0);

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.VALID);
        assertThat(result.evidence().chainId()).isZero();
        assertThat(result.evidence().transactionHash()).isEqualTo(SuiDigest.encode(tx.getTxHash()));
        assertThat(result.evidence().blockchain().provider()).isEqualTo("SUI");
        assertThat(result.evidence().blockchain().registryObjectId()).isEqualTo(properties.getSui().getRegistryId());
        assertThat(result.evidence().blockchain().checkpointSequenceNumber()).isEqualTo(75);
        assertThat(result.evidence().merkleProofMatches()).isTrue();
        assertThat(result.evidence().treeVersion()).isEqualTo(1);
        assertThat(result.publicSubjects()).extracting(CredentialVerificationView.PublicSubject::displayName).containsExactly("공개 학생");
    }

    @Test
    void providerSwitchCannotRelabelAnExistingKaiaProofOrQueryItOnSui() {
        AnchoredEvidence evidence = anchoredEvidence();
        AncChainTransaction tx = AncChainTransaction.pending(evidence.batch().getId(), null, null,
                ChainOperationType.ANCHOR_BATCH, "legacy-test", 1001, EthereumAddress.fromHex(properties.getContractAddress()).bytes(),
                "1", UtcTime.toLocalDateTime(ISSUED_AT));
        tx.prepare(new byte[] {1}, bytes(9, 32), 2L, bytes(3, 20), UtcTime.toLocalDateTime(ISSUED_AT));
        given(chainTransactionRepository.findByBatchIdAndOperationType(evidence.batch().getId(), ChainOperationType.ANCHOR_BATCH))
                .willReturn(Optional.of(tx));
        configureSui();

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.BLOCKCHAIN_CONFIGURATION_ERROR);
        assertThat(result.evidence().chainId()).isEqualTo(1001);
        assertThat(result.evidence().blockchain().provider()).isEqualTo("KAIA");
        assertThat(result.evidence().contractAddress()).isEqualTo(EthereumAddress.fromBytes(tx.getContractAddress()).hex());
        org.mockito.Mockito.verifyNoInteractions(blockchainAnchorPort);
    }

    @Test
    void allowlistedLegacyProofAndItsStatusKeyAreVerifiedOnKaiaWhileSuiRemainsTheActiveProvider() {
        AnchoredEvidence evidence = anchoredEvidence();
        ReflectionTestUtils.setField(evidence.batch(), "chainContext", null);
        String legacyContract = properties.getContractAddress();
        AncChainTransaction tx = AncChainTransaction.pending(evidence.batch().getId(), null, null,
                ChainOperationType.ANCHOR_BATCH, "legacy-routed", 1001,
                EthereumAddress.fromHex(legacyContract).bytes(), "1", UtcTime.toLocalDateTime(ISSUED_AT));
        tx.prepare(new byte[]{1, 2, 3}, bytes(9, 32), 2L, bytes(3, 20), UtcTime.toLocalDateTime(ISSUED_AT));
        byte[] originalCanonical = credential.getCanonicalBytes();
        given(chainTransactionRepository.findByBatchIdAndOperationType(evidence.batch().getId(), ChainOperationType.ANCHOR_BATCH))
                .willReturn(Optional.of(tx));
        configureSui();
        var allow = new BlockchainProperties.LegacyKaiaReadRoute();
        allow.setEnabled(true); allow.setChainId(1001); allow.setContractAddress(legacyContract);
        allow.setContractVersion("1"); allow.setRuntimeCodeHash("0x" + "11".repeat(32));
        allow.setRpcUrl("https://rpc.example.invalid"); properties.getLegacyKaiaReadRoutes().add(allow);
        try (var created = org.mockito.Mockito.mockConstruction(
                com.api.trekkey.domain.credential.infrastructure.blockchain.Web3jKaiaBlockchainAnchorAdapter.class)) {
            ReflectionTestUtils.setField(service, "verificationRouter", new BlockchainVerificationRouter(properties, blockchainAnchorPort));
            BlockchainAnchorPort legacy = created.constructed().getFirst();
            long recorded = ISSUED_AT.plusSeconds(150).getEpochSecond();
            stubValidChainEvidence(evidence, new BlockchainAnchorPort.OnChainCredentialStatus(
                    BlockchainAnchorPort.CredentialChainState.REVOKED, recorded - 10, recorded, 2, Hash32.ZERO), 0, legacy);
            given(legacy.getIssuerKey(evidence.issuerId(), 2)).willReturn(new BlockchainAnchorPort.OnChainIssuerKey(
                    EthereumAddress.fromBytes(bytes(4, 20)), ISSUED_AT.getEpochSecond(), 0, 0, true));

            CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

            assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.REVOKED);
            assertThat(result.evidence().blockchain().provider()).isEqualTo("KAIA");
            assertThat(result.evidence().chainId()).isEqualTo(1001);
            org.mockito.Mockito.verify(legacy).getIssuerKey(evidence.issuerId(), 1);
            org.mockito.Mockito.verify(legacy).getIssuerKey(evidence.issuerId(), 2);
            org.mockito.Mockito.verifyNoInteractions(blockchainAnchorPort);
            assertThat(credential.getCanonicalBytes()).isEqualTo(originalCanonical);
            assertThat(tx.getSignedRawTransaction()).containsExactly(1, 2, 3);
            assertThat(tx.getChainContext()).isNull();
            assertThat(evidence.batch().getChainContext()).isNull();
            assertThat(evidence.issuerKey().getChainContext()).isNull();
        }
    }

    @Test
    void malformedStoredRoutingIdentityProducesAConfigurationErrorWithoutThrowingOrInventingCoordinates() {
        AnchoredEvidence evidence = anchoredEvidence();
        ReflectionTestUtils.setField(evidence.batch(), "chainContext", "SUI|malformed");
        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);
        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.BLOCKCHAIN_CONFIGURATION_ERROR);
        assertThat(result.evidence().contractAddress()).isNull();
        assertThat(result.evidence().blockchain().provider()).isNull();
        org.mockito.Mockito.verifyNoInteractions(blockchainAnchorPort);
    }

    @Test
    void aLegacyAnchorCannotBorrowAnIssuerKeyBoundToSui() {
        AnchoredEvidence evidence = anchoredEvidence();
        ReflectionTestUtils.setField(evidence.issuerKey(), "chainContext", "SUI|testnet|aabbccdd|0x"
                + "11".repeat(32) + "|0x" + "22".repeat(32) + "|1");
        long anchored = ISSUED_AT.plusSeconds(30).getEpochSecond();
        given(blockchainAnchorPort.getBatch(Hash32.of(evidence.batch().getBatchIdHash())))
                .willReturn(new BlockchainAnchorPort.OnChainBatch(evidence.issuerId(),
                        Hash32.of(evidence.batch().getMerkleRoot()), Hash32.of(evidence.batch().getSchemaVersionHash()),
                        1, 1, 1, anchored, true));
        assertThat(service.verify(CREDENTIAL_PUBLIC_ID).verificationStatus()).isEqualTo(CredentialVerificationStatus.ISSUER_INVALID);
        org.mockito.Mockito.verify(blockchainAnchorPort).getBatch(Hash32.of(evidence.batch().getBatchIdHash()));
        org.mockito.Mockito.verifyNoMoreInteractions(blockchainAnchorPort);
    }

    @Test
    void changingSuiRegistryFailsClosedWhileDisplayingTheOriginalStoredRegistry() {
        AnchoredEvidence evidence = anchoredEvidence();
        configureSui();
        ReflectionTestUtils.setField(evidence.batch(), "chainContext", properties.chainContext());
        String originalRegistry = properties.getSui().getRegistryId();
        suiTransaction(evidence);
        properties.getSui().setRegistryId("0x" + "44".repeat(32));

        CredentialVerificationView result = service.verify(CREDENTIAL_PUBLIC_ID);

        assertThat(result.verificationStatus()).isEqualTo(CredentialVerificationStatus.BLOCKCHAIN_CONFIGURATION_ERROR);
        assertThat(result.evidence().blockchain().registryObjectId()).isEqualTo(originalRegistry);
        org.mockito.Mockito.verifyNoInteractions(blockchainAnchorPort);
    }

    private void configureSui() {
        properties.setProvider(BlockchainProperties.Provider.SUI);
        properties.getSui().setChainIdentifier("aabbccdd");
        properties.getSui().setPackageId("0x" + "11".repeat(32));
        properties.getSui().setRegistryId("0x" + "22".repeat(32));
    }

    private AncChainTransaction suiTransaction(AnchoredEvidence evidence) {
        AncChainTransaction tx = AncChainTransaction.pending(evidence.batch().getId(), null, null,
                ChainOperationType.ANCHOR_BATCH, "sui-test", 0, properties.ledgerContractAddress(), "1",
                UtcTime.toLocalDateTime(ISSUED_AT)).inContext(properties.chainContext());
        tx.prepare(new byte[] {1, 2}, bytes(5, 32), null, bytes(6, 32), UtcTime.toLocalDateTime(ISSUED_AT));
        tx.markSubmitted(UtcTime.toLocalDateTime(ISSUED_AT.plusSeconds(1)));
        tx.markConfirmed(75, bytes(7, 32), 0, UtcTime.toLocalDateTime(ISSUED_AT.plusSeconds(2)));
        given(chainTransactionRepository.findByBatchIdAndOperationType(evidence.batch().getId(), ChainOperationType.ANCHOR_BATCH))
                .willReturn(Optional.of(tx));
        return tx;
    }

    private AncCredential credential() throws Exception {
        FileManifest.Result manifest = FileManifest.build(List.of());
        CredentialIssueCommand command = command();
        byte[] canonicalBytes = CanonicalJson.canonicalize(CredentialPayloadFactory.create(
                CREDENTIAL_PUBLIC_ID,
                command,
                ORGANIZATION_PUBLIC_ID,
                "발급 당시 학교 이름",
                manifest.hash().hex()));
        Hash32 issuerId = Hashing.issuerId(ORGANIZATION_PUBLIC_ID);
        AncCredential value = AncCredential.ready(
                1L,
                CREDENTIAL_PUBLIC_ID,
                Hashing.credentialId(issuerId, CREDENTIAL_PUBLIC_ID).bytes(),
                command.credentialNo(),
                command.credentialType(),
                command.schemaProfileId(),
                Hashing.schemaVersion(command.schemaProfileId()).bytes(),
                new String(canonicalBytes, StandardCharsets.UTF_8),
                canonicalBytes,
                manifest.canonicalBytes(),
                Hashing.sha256(canonicalBytes).bytes(),
                manifest.hash().bytes(),
                UtcTime.toLocalDateTime(ISSUED_AT.truncatedTo(ChronoUnit.MICROS)),
                null);
        ReflectionTestUtils.setField(value, "id", 10L);
        return value;
    }

    private CredentialIssueCommand command() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return new CredentialIssueCommand(
                1L,
                "AWARD-2026-001",
                CredentialType.AWARD,
                CredentialSchemaProfiles.AWARD_V1,
                new CredentialIssueCommand.Source(
                        CredentialSourceType.AWARD,
                        null,
                        null,
                        30L,
                        "award-public-30",
                        Instant.parse("2026-07-24T01:00:00Z"),
                        mapper.readTree("""
                                {
                                  "contestTitle": "2026 캡스톤 경진대회",
                                  "submissionTitle": "트레키 작품",
                                  "prize": "대상",
                                  "awardRankNo": 1
                                }
                                """)),
                List.of(
                        new CredentialIssueCommand.Subject(
                                null,
                                20L,
                                "team-public-20",
                                CredentialSubjectType.TEAM,
                                "트레키 팀",
                                null,
                                "AWARDEE",
                                DisclosureClass.PRIVATE,
                                0),
                        new CredentialIssueCommand.Subject(
                                40L,
                                null,
                                "user-public-40",
                                CredentialSubjectType.USER,
                                "공개 학생",
                                "컴퓨터공학",
                                "REPRESENTATIVE",
                                DisclosureClass.PUBLIC,
                                1)),
                List.of(),
                ISSUED_AT,
                null);
    }

    private AnchoredEvidence anchoredEvidence() {
        credential.markBatched();
        credential.markAnchored();
        Hash32 issuerId = Hashing.issuerId(ORGANIZATION_PUBLIC_ID);
        CredentialLeaf leaf = new CredentialLeaf(
                issuerId,
                Hash32.of(credential.getCredentialIdHash()),
                Hash32.of(credential.getSchemaVersionHash()),
                Hash32.of(credential.getContentHash()),
                Hash32.of(credential.getFileManifestHash()));
        AncIssuerKey issuerKey = AncIssuerKey.activate(
                1L,
                1,
                bytes(7, 20),
                "issuer-key-1",
                LocalDateTime.ofInstant(ISSUED_AT.minusSeconds(60), ZoneOffset.UTC),
                null);
        ReflectionTestUtils.setField(issuerKey, "id", 20L);
        AncBatch batch = AncBatch.seal(
                1L,
                issuerKey.getId(),
                "batch-public-1",
                Hashing.batchId(issuerId, "batch-public-1").bytes(),
                credential.getSchemaVersionHash(),
                1,
                1,
                leaf.hash().bytes(),
                1,
                LocalDateTime.ofInstant(ISSUED_AT.plusSeconds(600), ZoneOffset.UTC),
                LocalDateTime.ofInstant(ISSUED_AT, ZoneOffset.UTC));
        ReflectionTestUtils.setField(batch, "id", 30L);
        ReflectionTestUtils.setField(batch, "chainContext", properties.chainContext());
        batch.recordApproval("{}", bytes(8, 32), bytes(9, 65),
                LocalDateTime.ofInstant(ISSUED_AT.plusSeconds(1), ZoneOffset.UTC));
        batch.beginAnchoring();
        batch.markAnchored();
        AncBatchItem item = AncBatchItem.of(
                batch.getId(),
                credential.getId(),
                0,
                credential.getCredentialIdHash(),
                leaf.hash().bytes(),
                "[]");
        given(batchItemRepository.findByCredentialId(credential.getId())).willReturn(Optional.of(item));
        given(batchRepository.findById(batch.getId())).willReturn(Optional.of(batch));
        lenient().when(issuerKeyRepository.findById(issuerKey.getId())).thenReturn(Optional.of(issuerKey));
        return new AnchoredEvidence(issuerId, issuerKey, batch);
    }

    private void stubValidChainEvidence(
            AnchoredEvidence evidence,
            BlockchainAnchorPort.OnChainCredentialStatus status,
            long compromisedAt) {
        stubValidChainEvidence(evidence, status, compromisedAt, blockchainAnchorPort);
    }

    private void stubValidChainEvidence(
            AnchoredEvidence evidence,
            BlockchainAnchorPort.OnChainCredentialStatus status,
            long compromisedAt,
            BlockchainAnchorPort reader) {
        long anchoredAt = ISSUED_AT.plusSeconds(30).getEpochSecond();
        given(reader.getBatch(Hash32.of(evidence.batch().getBatchIdHash())))
                .willReturn(new BlockchainAnchorPort.OnChainBatch(
                        evidence.issuerId(),
                        Hash32.of(evidence.batch().getMerkleRoot()),
                        Hash32.of(evidence.batch().getSchemaVersionHash()),
                        evidence.batch().getLeafCount(),
                        evidence.batch().getTreeVersion(),
                        evidence.issuerKey().getKeyVersion(),
                        anchoredAt,
                        true));
        given(reader.getIssuerKey(
                evidence.issuerId(),
                evidence.issuerKey().getKeyVersion()))
                .willReturn(new BlockchainAnchorPort.OnChainIssuerKey(
                        EthereumAddress.fromBytes(evidence.issuerKey().getSignerAddress()),
                        ISSUED_AT.minusSeconds(60).getEpochSecond(),
                        0,
                        compromisedAt,
                        true));
        lenient().when(reader.getCredentialStatus(
                        evidence.issuerId(),
                        Hash32.of(credential.getCredentialIdHash())))
                .thenReturn(status);
    }

    private static byte[] bytes(int firstByte, int length) {
        byte[] value = new byte[length];
        value[0] = (byte) firstByte;
        return value;
    }

    private record AnchoredEvidence(Hash32 issuerId, AncIssuerKey issuerKey, AncBatch batch) {
    }
}
