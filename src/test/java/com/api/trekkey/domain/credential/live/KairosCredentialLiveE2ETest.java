package com.api.trekkey.domain.credential.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.AncCredentialStatusEvent;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.service.CredentialBlockchainService;
import com.api.trekkey.domain.credential.service.CredentialIssuanceService;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.CredentialVerificationService;
import com.api.trekkey.domain.credential.service.dto.BlockchainApprovalView;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationStatus;
import com.api.trekkey.domain.credential.service.dto.IssuedCredential;
import com.api.trekkey.domain.credential.service.dto.IssuerKeyView;
import com.api.trekkey.domain.credential.service.dto.SealedBatchView;
import com.api.trekkey.domain.credential.service.dto.StatusChangeCommand;
import com.api.trekkey.domain.credential.service.worker.BlockchainWorkers;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Opt-in Kairos testnet proof. It uses an H2 ledger but submits real transactions through the
 * production Credential services, outbox worker, and Web3j adapter.
 *
 * <p>It is disabled unless {@code KAIROS_LIVE_E2E=true}. This test never logs private keys or
 * signatures.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kairos-live-e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.task.scheduling.enabled=false",
        "security.jwt.secret-key=c2VjdXJpdHktcmVhZC1vbmx5LXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "blockchain.anchoring.mode=LOCAL_RELAYER",
        "blockchain.anchoring.chain-id=1001",
        "blockchain.anchoring.runtime-code-hash=0x6bdcd078a99c833e1e6126d71954b570fb7bfb4afe4720fc4a438009039571a2",
        "blockchain.anchoring.contract-version=1",
        "blockchain.anchoring.tree-version=1",
        "blockchain.anchoring.batch-size=10",
        "blockchain.anchoring.approval-ttl=10m",
        "blockchain.anchoring.worker-enabled=true",
        "blockchain.anchoring.worker-claim-size=10",
        "blockchain.anchoring.worker-max-attempts=3",
        "blockchain.anchoring.receipt-polling-interval=1d",
        "blockchain.anchoring.receipt-timeout=45s"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "KAIROS_LIVE_E2E", matches = "true")
class KairosCredentialLiveE2ETest {

    private static final String DEFAULT_RPC_URL = "https://public-en-kairos.node.kaia.io";
    private static final String DEFAULT_CONTRACT_ADDRESS = "0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117";
    private static final Duration RECEIPT_TIMEOUT = Duration.ofSeconds(90);

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AncBatchRepository batchRepository;

    @Autowired
    private AncChainTransactionRepository chainTransactionRepository;

    @Autowired
    private CredentialIssuanceService credentialIssuanceService;

    @Autowired
    private CredentialBlockchainService credentialBlockchainService;

    @Autowired
    private CredentialVerificationService credentialVerificationService;

    @Autowired
    private BlockchainWorkers blockchainWorkers;

    @Autowired
    private MockMvc mockMvc;

    private Organization organization;
    private User actor;
    private org.web3j.crypto.ECKeyPair issuerKeyPair;
    private int issuerKeyVersion;

    @DynamicPropertySource
    static void blockchainProperties(DynamicPropertyRegistry registry) {
        registry.add("blockchain.anchoring.rpc-url", () -> environmentOrDefault("KAIROS_E2E_RPC_URL", DEFAULT_RPC_URL));
        registry.add(
                "blockchain.anchoring.contract-address",
                () -> environmentOrDefault("KAIROS_E2E_CONTRACT_ADDRESS", DEFAULT_CONTRACT_ADDRESS));
        registry.add("blockchain.anchoring.relayer-private-key", () -> requiredEnvironment("KAIROS_E2E_RELAYER_PRIVATE_KEY"));
    }

    @BeforeEach
    void setUpLedgerAndSyncIssuerKey() {
        String organizationPublicId = requiredEnvironment("KAIROS_E2E_ORGANIZATION_PUBLIC_ID");
        assertThatCodePointUuid(organizationPublicId, "KAIROS_E2E_ORGANIZATION_PUBLIC_ID");
        issuerKeyPair = keyPair(requiredEnvironment("KAIROS_E2E_ISSUER_PRIVATE_KEY"));
        issuerKeyVersion = integerEnvironment("KAIROS_E2E_ISSUER_KEY_VERSION", 1);

        organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "publicId", organizationPublicId);
        ReflectionTestUtils.setField(organization, "name", "Kairos Live E2E Organization");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        organization = organizationRepository.saveAndFlush(organization);

        actor = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("Kairos Live E2E Administrator")
                .email("kairos-e2e-" + UUID.randomUUID() + "@example.test")
                .password("not-used-by-live-e2e")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .department("Credential")
                .position("Administrator")
                .build());

        IssuerKeyView synchronizedKey = credentialBlockchainService.syncIssuerKey(
                organization.getId(), issuerKeyVersion, "kairos-live-e2e");
        assertThat(synchronizedKey.keyVersion()).isEqualTo(issuerKeyVersion);
        assertThat(synchronizedKey.status()).isEqualTo(IssuerKeyStatus.ACTIVE);
        assertThat(synchronizedKey.signerAddress())
                .isEqualTo(EthereumAddress.fromPublicKey(issuerKeyPair.getPublicKey()).hex());
    }

    @Test
    @Timeout(360)
    @DisplayName("Kairos에서 3개 Credential 앵커링 후 revoke와 supersede를 실제 worker로 검증한다")
    void anchorsVerifiesRevokesAndSupersedesCredentialsOnKairos() throws Exception {
        IssuedCredential revokedCredential = issueParticipationCredential(1);
        IssuedCredential supersededCredential = issueParticipationCredential(2);
        IssuedCredential replacementCredential = issueParticipationCredential(3);

        SealedBatchView batch = credentialBlockchainService.sealBatch(
                organization.getId(), CredentialSchemaProfiles.PARTICIPATION_V1, issuerKeyVersion);
        assertThat(batch.leafCount()).isEqualTo(3);
        credentialBlockchainService.approveBatch(
                organization.getId(), batch.publicId(), sign(credentialBlockchainService.getBatchApproval(
                        organization.getId(), batch.publicId())));

        AncBatch anchoredBatch = batchRepository.findByPublicId(batch.publicId()).orElseThrow();
        AncChainTransaction anchorTransaction = submitAndAwait(
                anchoredBatch.getId(), null, ChainOperationType.ANCHOR_BATCH);
        printAnchorEvidence(
                batch,
                anchorTransaction,
                revokedCredential.publicId(),
                supersededCredential.publicId(),
                replacementCredential.publicId());

        assertThat(credentialVerificationService.verify(revokedCredential.publicId()).verificationStatus())
                .isEqualTo(CredentialVerificationStatus.VALID);
        assertThat(credentialVerificationService.verify(supersededCredential.publicId()).verificationStatus())
                .isEqualTo(CredentialVerificationStatus.VALID);
        assertThat(credentialVerificationService.verify(replacementCredential.publicId()).verificationStatus())
                .isEqualTo(CredentialVerificationStatus.VALID);
        verifyQrTarget(revokedCredential.publicId(), CredentialVerificationStatus.VALID, null);

        BlockchainApprovalView revokeApproval = credentialBlockchainService.requestStatusChange(
                organization.getId(),
                actor.getId(),
                revokedCredential.publicId(),
                new StatusChangeCommand(
                        StatusChangeCommand.Action.REVOKE,
                        null,
                        issuerKeyVersion,
                        "KAIROS_LIVE_E2E_REVOKE",
                        "Kairos live E2E test revocation"));
        long revokeEventId = Long.parseLong(revokeApproval.aggregateId());
        credentialBlockchainService.approveStatusChange(organization.getId(), revokeEventId, sign(revokeApproval));
        AncChainTransaction revokeTransaction = submitAndAwait(null, revokeEventId, ChainOperationType.REVOKE);
        printStatusEvidence("REVOKE", revokeTransaction, revokedCredential.publicId(), null);

        BlockchainApprovalView supersedeApproval = credentialBlockchainService.requestStatusChange(
                organization.getId(),
                actor.getId(),
                supersededCredential.publicId(),
                new StatusChangeCommand(
                        StatusChangeCommand.Action.SUPERSEDE,
                        replacementCredential.publicId(),
                        issuerKeyVersion,
                        "KAIROS_LIVE_E2E_SUPERSEDE",
                        "Kairos live E2E test supersession"));
        long supersedeEventId = Long.parseLong(supersedeApproval.aggregateId());
        credentialBlockchainService.approveStatusChange(organization.getId(), supersedeEventId, sign(supersedeApproval));
        AncChainTransaction supersedeTransaction = submitAndAwait(null, supersedeEventId, ChainOperationType.SUPERSEDE);
        printStatusEvidence(
                "SUPERSEDE",
                supersedeTransaction,
                supersededCredential.publicId(),
                replacementCredential.publicId());

        assertThat(credentialVerificationService.verify(revokedCredential.publicId()).verificationStatus())
                .isEqualTo(CredentialVerificationStatus.REVOKED);
        assertThat(credentialVerificationService.verify(supersededCredential.publicId()).verificationStatus())
                .isEqualTo(CredentialVerificationStatus.SUPERSEDED);
        assertThat(credentialVerificationService.verify(supersededCredential.publicId()).replacementCredentialPublicId())
                .isEqualTo(replacementCredential.publicId());
        assertThat(credentialVerificationService.verify(replacementCredential.publicId()).verificationStatus())
                .isEqualTo(CredentialVerificationStatus.VALID);
        verifyQrTarget(revokedCredential.publicId(), CredentialVerificationStatus.REVOKED, null);
        verifyQrTarget(
                supersededCredential.publicId(),
                CredentialVerificationStatus.SUPERSEDED,
                replacementCredential.publicId());
        verifyQrTarget(replacementCredential.publicId(), CredentialVerificationStatus.VALID, null);
    }

    private IssuedCredential issueParticipationCredential(int sequence) {
        Instant issuedAt = Instant.now();
        long teamId = 10_000L + sequence;
        ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
        snapshot.put("workflow", "kairos-live-e2e");
        snapshot.put("sequence", sequence);
        snapshot.put("finalized", true);
        return credentialIssuanceService.issue(new CredentialIssueCommand(
                organization.getId(),
                "KAIROS-E2E-" + sequence + "-" + UUID.randomUUID(),
                CredentialType.PARTICIPATION,
                CredentialSchemaProfiles.PARTICIPATION_V1,
                new CredentialIssueCommand.Source(
                        CredentialSourceType.TEAM,
                        teamId,
                        null,
                        null,
                        "kairos-live-e2e-team-" + sequence + "-" + UUID.randomUUID(),
                        issuedAt.minusSeconds(1),
                        snapshot),
                java.util.List.of(
                        new CredentialIssueCommand.Subject(
                                null,
                                teamId,
                                "team:"
                                        + teamId,
                                CredentialSubjectType.TEAM,
                                "Kairos Live E2E Team " + sequence,
                                null,
                                "TEAM",
                                DisclosureClass.PUBLIC,
                                0),
                        new CredentialIssueCommand.Subject(
                                actor.getId(),
                                null,
                                "user:"
                                        + actor.getId(),
                                CredentialSubjectType.USER,
                                actor.getName(),
                                null,
                                "PARTICIPANT",
                                DisclosureClass.PUBLIC,
                                1)),
                java.util.List.of(),
                issuedAt,
                null));
    }

    private String sign(BlockchainApprovalView approval) {
        return Eip712.signDigest(Hash32.fromHex(approval.digestHex()), issuerKeyPair).hex();
    }

    private AncChainTransaction submitAndAwait(
            Long batchId,
            Long statusEventId,
            ChainOperationType operationType) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + RECEIPT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            blockchainWorkers.submitPendingOutbox();
            blockchainWorkers.reconcileReceipts();
            AncChainTransaction transaction = findTransaction(batchId, statusEventId, operationType);
            if (transaction.getStatus() == ChainTransactionStatus.CONFIRMED) {
                return transaction;
            }
            assertThat(transaction.getStatus())
                    .as("%s transaction must not fail", operationType)
                    .isNotEqualTo(ChainTransactionStatus.FAILED);
            Thread.sleep(500);
        }
        AncChainTransaction transaction = findTransaction(batchId, statusEventId, operationType);
        throw new AssertionError(operationType + " receipt did not confirm, last status=" + transaction.getStatus());
    }

    private AncChainTransaction findTransaction(Long batchId, Long statusEventId, ChainOperationType operationType) {
        if (batchId != null) {
            return chainTransactionRepository.findByBatchIdAndOperationType(batchId, operationType).orElseThrow();
        }
        return chainTransactionRepository
                .findByCredentialStatusEventIdAndOperationType(statusEventId, operationType)
                .orElseThrow();
    }

    private void assertConfirmedEvidence(AncChainTransaction transaction) {
        assertThat(transaction.getTxHash()).hasSize(32);
        assertThat(transaction.getBlockNumber()).isPositive();
        assertThat(transaction.getEventLogIndex()).isGreaterThanOrEqualTo(0);
    }

    private void printAnchorEvidence(
            SealedBatchView batch,
            AncChainTransaction transaction,
            String... credentialPublicIds) {
        assertConfirmedEvidence(transaction);
        System.out.printf(
                Locale.ROOT,
                "KAIROS_LIVE_E2E ANCHOR txHash=0x%s block=%d batchPublicId=%s"
                        + " merkleRoot=%s leafCount=%d credentialPublicIds=%s%n",
                java.util.HexFormat.of().formatHex(transaction.getTxHash()),
                transaction.getBlockNumber(),
                batch.publicId(),
                batch.merkleRoot(),
                batch.leafCount(),
                String.join(",", credentialPublicIds));
    }

    private void printStatusEvidence(
            String operation,
            AncChainTransaction transaction,
            String credentialPublicId,
            String replacementCredentialPublicId) {
        assertConfirmedEvidence(transaction);
        System.out.printf(
                Locale.ROOT,
                "KAIROS_LIVE_E2E %s txHash=0x%s block=%d credentialPublicId=%s replacement=%s%n",
                operation,
                java.util.HexFormat.of().formatHex(transaction.getTxHash()),
                transaction.getBlockNumber(),
                credentialPublicId,
                replacementCredentialPublicId == null ? "-" : replacementCredentialPublicId);
    }

    private void verifyQrTarget(
            String credentialPublicId,
            CredentialVerificationStatus expectedStatus,
            String replacementCredentialPublicId) throws Exception {
        var result = mockMvc.perform(get("/api/public/credentials/{credentialPublicId}", credentialPublicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialPublicId").value(credentialPublicId))
                .andExpect(jsonPath("$.data.verificationStatus").value(expectedStatus.name()));
        if (replacementCredentialPublicId != null) {
            result.andExpect(jsonPath("$.data.replacementCredentialPublicId")
                    .value(replacementCredentialPublicId));
        }
        System.out.printf(
                Locale.ROOT,
                "KAIROS_LIVE_E2E QR_TARGET=/api/public/credentials/%s status=%s%n",
                credentialPublicId,
                expectedStatus);
    }

    private static org.web3j.crypto.ECKeyPair keyPair(String privateKey) {
        String normalized = privateKey.replaceFirst("^0x", "");
        if (!normalized.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("KAIROS_E2E_ISSUER_PRIVATE_KEY must be a 32-byte hex key");
        }
        return org.web3j.crypto.ECKeyPair.create(new BigInteger(normalized, 16));
    }

    private static int integerEnvironment(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException("must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(name + " must be a positive integer", exception);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when KAIROS_LIVE_E2E=true");
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static void assertThatCodePointUuid(String value, String name) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " must be a UUID assigned to the on-chain issuer", exception);
        }
    }
}
