package com.api.trekkey.domain.credential.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import com.api.trekkey.domain.contest.entity.*;
import com.api.trekkey.domain.contest.repository.*;
import com.api.trekkey.domain.team.entity.*;
import com.api.trekkey.domain.team.repository.*;
import com.api.trekkey.domain.submission.repository.*;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Base64;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.crypto.SuiApproval;
import com.api.trekkey.domain.credential.crypto.SuiDigest;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.worker.BlockchainWorkTransactions;
import com.api.trekkey.domain.credential.service.worker.BlockchainWorkers;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

/** Explicit opt-in: persistent synthetic MySQL business lifecycle, three Sui anchors and one revoke.
 * No automatic cleanup, schema creation, existing H2 test reuse, or unbounded re-run. */
@EnabledIfEnvironmentVariable(named = "SUI_FULL_E2E", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "security.jwt.secret-key=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==",
        "security.jwt.access-expiration=3600", "security.jwt.refresh-expiration=7200",
        "app.cors.allowed-origin=http://localhost:3000", "app.front.base-url=http://localhost:3000",
        "blockchain.anchoring.provider=SUI", "blockchain.anchoring.mode=LOCAL_RELAYER",
        "blockchain.anchoring.worker-enabled=false", "blockchain.anchoring.worker-claim-size=1",
        "blockchain.anchoring.sui.network=testnet", "blockchain.anchoring.chain-id=0",
        "blockchain.anchoring.contract-version=1", "blockchain.anchoring.approval-ttl=15m",
        "blockchain.anchoring.sui.request-timeout=60s", "blockchain.anchoring.receipt-timeout=60s"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(SuiPersistentWorkflowIntegrationTest.IsolatedDatabase.class)
class SuiPersistentWorkflowIntegrationTest {
    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:13306/trekkey_sui_full"
            + "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
    private static Path runDirectory;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode manifest;
    private static Path stateDirectory;

    @TestConfiguration(proxyBeanMethods = false)
    static class IsolatedDatabase {
        @Bean
        DataSource dataSource() throws Exception {
            requireGate();
            // Explicit dedicated datasource: inherited Hikari URLs cannot redirect this test.
            DriverManagerDataSource source = new DriverManagerDataSource(MYSQL_URL, "trekkey_sui_full",
                    privateText(Path.of(requiredEnv("SUI_FULL_DB_PASSWORD_FILE")), 4096));
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return source;
        }
    }

    @DynamicPropertySource
    static void isolatedConfiguration(DynamicPropertyRegistry registry) throws Exception {
        requireGate(); // Defense in depth: context creation may never bypass the JUnit gate.
        stateDirectory = Path.of(requiredEnv("SUI_E2E_STATE_DIR"));
        require(stateDirectory.isAbsolute() && Files.isDirectory(stateDirectory, LinkOption.NOFOLLOW_LINKS),
                "SUI_E2E_STATE_DIR must be an existing absolute directory, not a symlink");
        ownerOnly(stateDirectory);
        Path deployment = stateDirectory.resolve("deployment.json");
        require(Files.isRegularFile(deployment, LinkOption.NOFOLLOW_LINKS) && Files.size(deployment) < 100_000,
                "A bounded regular deployment.json is required");
        manifest = JSON.readTree(Files.readString(deployment));
        require(manifest.path("synthetic").asBoolean(false) && "testnet".equals(manifest.path("network").asText()),
                "Only an explicitly synthetic testnet deployment is allowed");
        require(manifest.path("protocolVersion").asInt() == 1 && manifest.path("keyVersion").asInt() > 0,
                "Manifest protocol/key version is invalid");
        String organizationPublicId = manifest.path("organizationPublicId").asText();
        require(UUID.fromString(organizationPublicId).toString().equals(organizationPublicId), "A canonical synthetic organization UUID is required");
        require(Hashing.issuerId(organizationPublicId).hex().equals(manifest.path("issuerId").asText()),
                "Manifest issuerId does not match the synthetic organization UUID");
        new SuiApproval.Domain(text(manifest, "chainIdentifier"), text(manifest, "packageId"), text(manifest, "registryId"));
        String gateway = System.getenv().getOrDefault("SUI_E2E_GATEWAY_URL", "http://127.0.0.1:9187");
        URI origin = URI.create(gateway);
        require(Set.of("127.0.0.1", "localhost", "[::1]").contains(origin.getHost())
                        && Set.of("http", "https").contains(origin.getScheme()) && origin.getUserInfo() == null
                        && origin.getQuery() == null && origin.getFragment() == null
                        && (origin.getPath().isEmpty() || origin.getPath().equals("/")),
                "E2E gateway must be a loopback HTTP(S) origin");
        String tokenFile = System.getenv("SUI_GATEWAY_TOKEN_FILE");
        String token;
        if (tokenFile != null && !tokenFile.isBlank()) {
            Path file = Path.of(tokenFile);
            require(file.isAbsolute() && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 4097,
                    "Gateway token file must be a bounded absolute regular file, not a symlink");
            ownerOnly(file);
            token = Files.readString(file).strip();
        } else {
            token = requiredEnv("SUI_GATEWAY_TOKEN");
        }
        require(token.matches("[!-~]{32,4096}"), "Gateway token must be 32..4096 non-whitespace ASCII characters");
        registry.add("spring.datasource.url", () -> MYSQL_URL);
        runDirectory = Path.of(requiredEnv("SUI_FULL_RUN_DIR"));
        require(Files.isDirectory(runDirectory, LinkOption.NOFOLLOW_LINKS) && runDirectory.isAbsolute(), "Existing absolute run directory required");
        ownerOnly(runDirectory);
        Path uploads = Path.of(requiredEnv("SUI_FULL_UPLOAD_DIR"));
        require(uploads.isAbsolute() && Files.isDirectory(uploads, LinkOption.NOFOLLOW_LINKS), "Existing dedicated uploads directory required");
        ownerOnly(uploads);
        registry.add("app.file.upload-dir", () -> uploads.toString());
        registry.add("blockchain.anchoring.sui.chain-identifier", () -> text(manifest, "chainIdentifier"));
        registry.add("blockchain.anchoring.sui.package-id", () -> text(manifest, "packageId"));
        registry.add("blockchain.anchoring.sui.registry-id", () -> text(manifest, "registryId"));
        registry.add("blockchain.anchoring.sui.gateway-url", () -> gateway);
        registry.add("blockchain.anchoring.sui.gateway-token", () -> token);
    }

    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired AncCredentialRepository credentials;
    @Autowired AncChainTransactionRepository chainTransactions;
    @Autowired AncOutboxEventRepository outbox;
    @Autowired AncBatchRepository batches;
    @Autowired AncCredentialStatusEventRepository statusEvents;
    @Autowired BlockchainWorkTransactions workTransactions;
    @Autowired BlockchainAnchorPort anchor;
    @Autowired BlockchainProperties properties;
    @Autowired Clock credentialClock;
    @Autowired JwtTokenProvider tokens;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ContestRepository contests;
    @Autowired ContestStageRepository stages;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository teamMembers;
    @Autowired SubmissionRepository submissions;
    private String adminToken;
    private int approvedOperations;
    private final Map<String, String> credentialIds = new LinkedHashMap<>();
    private final Map<String, Object> evidenceIds = new LinkedHashMap<>();
    private List<String> fixturePlaintexts;

    @Test
    void persistentThreeTypesFollowBusinessTransitionsThenAnchorAndRevoke() throws Exception {
        requireGate();
        try (var connection = dataSource.getConnection()) {
            require(connection.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:13306/trekkey_sui_full?"),
                    "Only the explicitly dedicated MySQL schema is allowed");
            require("trekkey_sui_full".equals(connection.getCatalog()), "Unexpected schema");
        }
        assertThat(properties.isWorkerEnabled()).isFalse();
        assertThat(properties.getProvider()).isEqualTo(BlockchainProperties.Provider.SUI);
        assertThat(properties.getSui().getNetwork()).isEqualTo("testnet");
        require("50000000".equals(requiredEnv("SUI_FULL_GAS_BUDGET_MIST")),
                "Operator must verify gateway gas budget is exactly 50000000 MIST; four operations maximum");
        ECKeyPair issuerKey = syntheticIssuerKey();
        String loginPassword = privateText(Path.of(requiredEnv("SUI_FULL_LOGIN_PASSWORD_FILE")), 256);
        require(loginPassword.matches("[!-~]{16,64}"),
                "Synthetic login password must be 16..64 printable ASCII characters");
        String resume = System.getenv("SUI_FULL_RESUME");
        if (resume != null) {
            require("two-confirmed-anchors".equals(resume), "Only the explicitly reviewed resume phase is allowed");
            resumeTwoConfirmedAnchors(issuerKey);
            return;
        }
        // No cleanup: the protected claim survives success/failure and prevents fresh duplicate runs.
        persist("run.claim", Map.of("network", "testnet", "database", "trekkey_sui_full",
                "maximumOperations", 4, "maximumGasBudgetMist", "200000000"));
        require(chainTransactions.count() == 0 && outbox.count() == 0 && credentials.count() == 0,
                "Full lifecycle requires no existing credential/outbox work; preserve state and reconcile instead");
        String organizationId = text(manifest, "organizationPublicId");
        Organization org = organizations.findAll().stream()
                .filter(candidate -> organizationId.equals(candidate.getPublicId())).findFirst().orElse(null);
        if (org == null) {
            require(organizations.findByCode("HANSUNG_UNIVERSITY").isEmpty(), "Synthetic school code already belongs to another organization");
            org = BeanUtils.instantiateClass(Organization.class);
            ReflectionTestUtils.setField(org, "publicId", organizationId);
            ReflectionTestUtils.setField(org, "code", "HANSUNG_UNIVERSITY");
            ReflectionTestUtils.setField(org, "name", "SYNTHETIC Sui Full E2E — NOT A REAL INSTITUTION");
            ReflectionTestUtils.setField(org, "status", OrganizationStatus.ACTIVE);
            org = organizations.saveAndFlush(org);
        }
        require(org.getStatus() == OrganizationStatus.ACTIVE, "Synthetic organization must be active");
        User admin = users.saveAndFlush(User.builder().organization(org).name("Synthetic Full Admin")
                .email("sui-full-admin-" + UUID.randomUUID() + "@example.invalid").password(passwordEncoder.encode(loginPassword))
                .role(UserRole.ADMIN).memberType(MemberType.STAFF).status(UserStatus.ACTIVE).build());
        User student = users.saveAndFlush(User.builder().organization(org).name("Synthetic Full Student")
                .email("sui-full-student-" + UUID.randomUUID() + "@example.invalid").password(passwordEncoder.encode(loginPassword))
                .role(UserRole.PARTICIPANT).memberType(MemberType.STUDENT).status(UserStatus.ACTIVE).major("Synthetic").build());
        User secondAdmin = users.saveAndFlush(User.builder().organization(org).name("Synthetic Full Second Admin")
                .email("sui-full-second-admin-" + UUID.randomUUID() + "@example.invalid").password(passwordEncoder.encode(loginPassword))
                .role(UserRole.ADMIN).memberType(MemberType.STAFF).status(UserStatus.ACTIVE).build());
        adminToken = tokenFor(admin, "ADMIN");
        String studentToken = tokenFor(student, "PARTICIPANT");
        // Seed only the synthetic contest/team and a short upload window. The credential-producing
        // transitions below all run through real secured MVC/controller/service transactions.
        Contest contest = contests.saveAndFlush(Contest.builder().organization(org).ownerUser(admin)
                .title("SYNTHETIC Sui persistent full workflow").department("Synthetic")
                .status(ContestStatus.APPLICATION_OPEN).participationType(ParticipationType.TEAM).maxTeamMembers(1)
                .awardCount(1).summary("Synthetic fixture only").target("Synthetic").applicationMethod("Synthetic")
                .benefits("Synthetic").detailHtml("<p>Synthetic fixture only</p>").build());
        LocalDateTime uploadDeadline = LocalDateTime.now().plusSeconds(45);
        stages.saveAndFlush(ContestStage.builder().contest(contest).name("Synthetic submission")
                .stageType(StageType.SUBMISSION).sequenceNo(1).status(StageStatus.OPEN)
                .startsAt(LocalDateTime.now().minusMinutes(1)).endsAt(uploadDeadline).build());
        Team team = teams.saveAndFlush(Team.builder().contest(contest).leaderUser(student)
                .name("Synthetic Full Team").leaderName(student.getName()).major("Synthetic").memberCount(1)
                .status(TeamStatus.PENDING).contactEmail("synthetic@example.invalid").phone("00000000000")
                .motivation("Synthetic fixture only").build());
        teamMembers.saveAndFlush(TeamMember.builder().team(team).user(student).role(TeamMemberRole.LEADER).build());
        evidenceIds.put("organizationPublicId", organizationId);
        evidenceIds.put("organizationDatabaseId", org.getId());
        evidenceIds.put("contestPublicId", contest.getPublicId());
        evidenceIds.put("teamPublicId", team.getPublicId());
        evidenceIds.put("adminUserId", admin.getId());
        evidenceIds.put("studentUserId", student.getId());
        evidenceIds.put("adminEmail", admin.getEmail());
        evidenceIds.put("studentEmail", student.getEmail());
        evidenceIds.put("secondAdminUserId", secondAdmin.getId());
        evidenceIds.put("secondAdminEmail", secondAdmin.getEmail());
        fixturePlaintexts = List.of(admin.getName(), admin.getEmail(), student.getName(), student.getEmail(),
                secondAdmin.getName(), secondAdmin.getEmail(), team.getName(), contest.getTitle(),
                "synthetic.txt", "SYNTHETIC TREKKEY TEST FILE - NO PERSONAL DATA");
        persist("01-fixture.json", evidenceIds);
        BlockchainProperties manual = new BlockchainProperties();
        BeanUtils.copyProperties(properties, manual);
        manual.setWorkerEnabled(true);
        BlockchainWorkers worker = new BlockchainWorkers(workTransactions, anchor, manual, credentialClock);
        try {
            var upload = multipart("/api/teams/" + team.getPublicId() + "/submission")
                    .file(new MockMultipartFile("files", "synthetic.txt", "text/plain",
                            "SYNTHETIC TREKKEY TEST FILE - NO PERSONAL DATA".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .param("title", "Synthetic work").with(request -> { request.setMethod("PUT"); return request; });
            upload.header("Authorization", "Bearer " + studentToken);
            String submissionId = response(upload, 200).path("id").asText();
            require(!submissionId.isBlank(), "Submission public ID missing");
            evidenceIds.put("submissionPublicId", submissionId);
            admin(patch("/api/admin/teams/" + team.getPublicId() + "/status"), Map.of("status", "APPROVED"), 200);
            admin(post("/api/admin/teams/" + team.getPublicId() + "/finalize"), null, 200);
            rememberCredential(CredentialType.PARTICIPATION);
            // MANUAL/MANUAL with no criteria is a supported product path, not a fake review.
            JsonNode round = admin(post("/api/admin/contests/" + contest.getPublicId() + "/review-rounds"),
                    Map.of("roundNo", 1, "name", "Synthetic manual final", "startsAt", uploadDeadline.plusSeconds(1).toString(),
                            "endsAt", LocalDateTime.now().plusMinutes(20).toString(), "targetType", "MANUAL",
                            "decisionRule", "MANUAL", "criteria", List.of()), 201);
            long roundId = round.path("id").asLong();
            require(roundId > 0, "Review round ID missing");
            evidenceIds.put("reviewRoundId", roundId);
            String roundPath = "/api/admin/contests/" + contest.getPublicId() + "/review-rounds/" + roundId;
            JsonNode entries = admin(post(roundPath + "/entries/prepare"),
                    Map.of("submissionPublicIds", List.of(submissionId)), 200);
            require(entries.isArray() && entries.size() == 1, "Exactly one synthetic review entry required");
            long entryId = entries.get(0).path("id").asLong();
            while (!LocalDateTime.now().isAfter(uploadDeadline.plusSeconds(1))) Thread.sleep(250);
            admin(post(roundPath + "/open"), null, 200);
            rememberCredential(CredentialType.WORK);
            assertThat(submissions.findByPublicId(submissionId).orElseThrow().isFinalized()).isTrue();
            admin(post(roundPath + "/finalize"), Map.of("manualDecisions", List.of(Map.of(
                    "entryId", entryId, "status", "SELECTED", "reason", "SYNTHETIC decision only", "rankNo", 1))), 200);
            JsonNode awards = admin(post("/api/admin/review-rounds/" + roundId + "/awards"), null, 200);
            require(awards.isArray() && awards.size() == 1, "Exactly one synthetic award required");
            admin(post("/api/admin/contests/" + contest.getPublicId() + "/awards/confirm"), null, 200);
            rememberCredential(CredentialType.AWARD);
            assertThat(contests.findById(contest.getId()).orElseThrow().getStatus()).isEqualTo(ContestStatus.AWARDED);
            assertThat(credentials.count()).isEqualTo(3);
            persist("02-business-credentials.json", Map.of("fixture", evidenceIds, "credentials", credentialIds));

            int keyVersion = manifest.path("keyVersion").asInt();
            JsonNode synced = admin(post("/api/admin/blockchain/issuer-keys/" + keyVersion + "/sync"),
                    Map.of("signerRef", "synthetic-full-e2e-external-issuer"), 201);
            assertThat(synced.path("signerAddress").asText()).isEqualTo(text(manifest, "signerAddress"));
            for (CredentialType type : List.of(CredentialType.PARTICIPATION, CredentialType.WORK, CredentialType.AWARD)) {
                String schema = switch (type) {
                    case PARTICIPATION -> CredentialSchemaProfiles.PARTICIPATION_V1;
                    case WORK -> CredentialSchemaProfiles.WORK_V1;
                    case AWARD -> CredentialSchemaProfiles.AWARD_V1;
                };
                String batchId = admin(post("/api/admin/blockchain/batches"),
                        Map.of("schemaProfileId", schema, "keyVersion", keyVersion), 201).path("publicId").asText();
                JsonNode approval = admin(get("/api/admin/blockchain/batches/" + batchId + "/approval"), null, 200);
                assertThat(JSON.readTree(approval.path("typedDataJson").asText()).path("message").path("leafCount").asLong()).isEqualTo(1);
                persist("batch-" + type.name().toLowerCase() + ".json", Map.of("batchPublicId", batchId, "credentialPublicId", credentialIds.get(type.name())));
                admin(post("/api/admin/blockchain/batches/" + batchId + "/approval"),
                        Map.of("signatureHex", sign(approval, issuerKey, "BatchApproval")), 200);
                JsonNode valid = awaitPublicStatus(credentialIds.get(type.name()), "VALID", worker);
                assertSuiEvidence(valid);
                emitPublicEvidence("VALID", valid);
                assertPublicDownloads(credentialIds.get(type.name()));
            }
            String revokedId = credentialIds.get("PARTICIPATION");
            JsonNode statusApproval = admin(post("/api/admin/blockchain/credentials/" + revokedId + "/status-events"),
                    Map.of("action", "REVOKE", "issuerKeyVersion", keyVersion, "reasonCode", "SYNTHETIC_FULL_E2E",
                            "reasonDetail", "Synthetic participation test record only"), 201);
            persist("status-revoke.json", Map.of("credentialPublicId", revokedId, "statusEventId", statusApproval.path("aggregateId").asText()));
            admin(post("/api/admin/blockchain/status-events/" + statusApproval.path("aggregateId").asText() + "/approval"),
                    Map.of("signatureHex", sign(statusApproval, issuerKey, "StatusApproval")), 200);
            JsonNode revoked = awaitPublicStatus(revokedId, "REVOKED", worker);
            assertSuiEvidence(revoked);
            assertPublicDownloads(revokedId);
            emitPublicEvidence("REVOKED", revoked);
            assertThat(chainTransactions.findAll()).hasSize(4).allSatisfy(tx -> {
                assertThat(tx.getStatus()).isEqualTo(ChainTransactionStatus.CONFIRMED);
                assertThat(tx.getTxNonce()).isNull(); assertThat(tx.getRelayerAddress()).hasSize(32);
                assertThat(tx.getSignedRawTransaction()).isNotEmpty();
                assertThat(tx.getBlockNumber()).isNotNull(); assertThat(tx.getBlockHash()).hasSize(32);
                assertThat(tx.getChainContext()).isEqualTo(properties.chainContext());
            });
            assertThat(outbox.findAll()).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSED));
            for (var tx : chainTransactions.findAll()) assertNoFixturePlaintext(tx.getSignedRawTransaction());
            persist("result.json", Map.of("status", "PASS", "database", "trekkey_sui_full",
                    "fixture", evidenceIds, "credentials", credentialIds, "transactions", publicTransactions()));
            System.out.println("SUI_FULL_RESULT " + JSON.writeValueAsString(Map.of(
                    "status", "PASS", "fixture", evidenceIds, "credentials", credentialIds, "transactions", publicTransactions())));
        } finally {
            for (var item : publicTransactions()) System.out.println("SUI_FULL_TRANSACTION " + JSON.writeValueAsString(item));
        }
    }

    private String tokenFor(User user, String role) {
        AuthPrincipal principal = AuthPrincipal.of(user.getId(), user.getEmail(), List.of(role));
        return tokens.createAccessToken(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** One bounded recovery for the observed failed run, never a fresh fixture or existing approval renewal. */
    private void resumeTwoConfirmedAnchors(ECKeyPair issuerKey) throws Exception {
        Set<String> expectedOriginalDigests = Set.of(
                "391PcEPC2irKkmga4ybFUArZMYvkzByyDsELTuDRxk6C",
                "4pJJ6px463hFXxvEKUD5MQ16jrWZVoACjkUtppoANej6");
        JsonNode claim = readState("run.claim"), fixture = readState("01-fixture.json"), business = readState("02-business-credentials.json");
        require("testnet".equals(claim.path("network").asText()) && "trekkey_sui_full".equals(claim.path("database").asText())
                && claim.path("maximumOperations").asInt() == 4, "Original persistent run claim is required");
        require(text(manifest, "organizationPublicId").equals(fixture.path("organizationPublicId").asText())
                && text(manifest, "organizationPublicId").equals(business.path("fixture").path("organizationPublicId").asText()),
                "Original synthetic organization identity mismatch");
        for (CredentialType type : CredentialType.values()) credentialIds.put(type.name(), text(business.path("credentials"), type.name()));
        require("c616a240-e5be-4021-b144-f78e879b9a2b".equals(credentialIds.get("PARTICIPATION"))
                && "54a0c3ec-925c-4b41-bcb0-36cfbc487db3".equals(credentialIds.get("WORK")), "Resume targets differ from the failed run");
        require(credentials.count() == 3 && batches.count() == 2 && statusEvents.count() == 0
                && outbox.count() == 2 && chainTransactions.count() == 2, "Unexpected additional credential/chain work; stop and reconcile");
        require(outbox.findAll().stream().allMatch(event -> event.getStatus() == OutboxStatus.PROCESSED), "Original outbox must be processed");
        Map<String, byte[]> originalEnvelopes = new LinkedHashMap<>();
        for (var tx : chainTransactions.findAll()) {
            String digest = SuiDigest.encode(tx.getTxHash());
            require(tx.getStatus() == ChainTransactionStatus.CONFIRMED && expectedOriginalDigests.contains(digest)
                    && tx.getOperationType() == com.api.trekkey.domain.credential.entity.ChainOperationType.ANCHOR_BATCH
                    && properties.chainContext().equals(tx.getChainContext()), "Original confirmed transaction identity mismatch");
            originalEnvelopes.put(digest, tx.getSignedRawTransaction());
        }
        require(originalEnvelopes.keySet().equals(expectedOriginalDigests), "Both original digests must be preserved");
        Map<String, byte[]> originalCanonical = new LinkedHashMap<>();
        Map<String, byte[]> originalContentHashes = new LinkedHashMap<>();
        for (CredentialType type : CredentialType.values()) {
            var credential = credentials.findByPublicId(credentialIds.get(type.name())).orElseThrow();
            require(credential.getCredentialType() == type && credential.getIssuerOrganizationId().equals(fixture.path("organizationDatabaseId").asLong())
                    && credential.getStatus() == (type == CredentialType.AWARD ? CredentialStatus.READY : CredentialStatus.ANCHORED),
                    "Persisted credential identity/state differs from the failed run");
            originalCanonical.put(credential.getPublicId(), credential.getCanonicalBytes());
            originalContentHashes.put(credential.getPublicId(), credential.getContentHash());
        }
        for (String type : List.of("participation", "work")) {
            JsonNode saved = readState("batch-" + type + ".json");
            var batch = batches.findByPublicId(text(saved, "batchPublicId")).orElseThrow();
            require(batch.getIssuerOrganizationId().equals(fixture.path("organizationDatabaseId").asLong())
                    && properties.chainContext().equals(batch.getChainContext()), "Original batch identity mismatch");
        }
        for (String pendingFile : List.of("batch-award.json", "status-revoke.json", "result.json", "resume-two-anchors.claim")) {
            require(!Files.exists(runDirectory.resolve(pendingFile), LinkOption.NOFOLLOW_LINKS), "Resume is one-shot; existing phase must be reconciled first");
        }
        User admin = users.findById(fixture.path("adminUserId").asLong()).orElseThrow();
        User student = users.findById(fixture.path("studentUserId").asLong()).orElseThrow();
        User secondAdmin = users.findById(fixture.path("secondAdminUserId").asLong()).orElseThrow();
        require(admin.getRole() == UserRole.ADMIN && admin.getStatus() == UserStatus.ACTIVE
                && admin.getEmail().equals(text(fixture, "adminEmail")), "Original synthetic administrator required");
        adminToken = tokenFor(admin, "ADMIN");
        fixture.fields().forEachRemaining(entry -> evidenceIds.put(entry.getKey(), JSON.convertValue(entry.getValue(), Object.class)));
        fixturePlaintexts = List.of(admin.getName(), admin.getEmail(), student.getName(), student.getEmail(),
                secondAdmin.getName(), secondAdmin.getEmail(), "Synthetic Full Team", "SYNTHETIC Sui persistent full workflow",
                "synthetic.txt", "SYNTHETIC TREKKEY TEST FILE - NO PERSONAL DATA");
        // Read-only proof first: the fixed verifier MUST validate the unchanged original anchors.
        for (String type : List.of("PARTICIPATION", "WORK")) {
            JsonNode valid = response(get("/api/public/credentials/" + credentialIds.get(type)), 200);
            require("VALID".equals(valid.path("verificationStatus").asText()), "Original anchors must be VALID before any resume write");
            assertSuiEvidence(valid); assertPublicDownloads(credentialIds.get(type)); emitPublicEvidence("VALID", valid);
        }
        persist("resume-two-anchors.claim", Map.of("network", "testnet", "database", "trekkey_sui_full",
                "preservedDigests", expectedOriginalDigests, "additionalOperations", 2, "additionalGasBudgetMist", "100000000"));
        approvedOperations = 2; // Count existing approvals toward the original four-operation budget.
        BlockchainProperties manual = new BlockchainProperties(); BeanUtils.copyProperties(properties, manual); manual.setWorkerEnabled(true);
        BlockchainWorkers worker = new BlockchainWorkers(workTransactions, anchor, manual, credentialClock);
        int keyVersion = manifest.path("keyVersion").asInt();
        try {
            String batchId = admin(post("/api/admin/blockchain/batches"),
                    Map.of("schemaProfileId", CredentialSchemaProfiles.AWARD_V1, "keyVersion", keyVersion), 201).path("publicId").asText();
            JsonNode approval = admin(get("/api/admin/blockchain/batches/" + batchId + "/approval"), null, 200);
            require(JSON.readTree(approval.path("typedDataJson").asText()).path("message").path("leafCount").asInt() == 1,
                    "Only the existing one AWARD credential may be sealed");
            persist("batch-award.json", Map.of("batchPublicId", batchId, "credentialPublicId", credentialIds.get("AWARD")));
            admin(post("/api/admin/blockchain/batches/" + batchId + "/approval"), Map.of("signatureHex", sign(approval, issuerKey, "BatchApproval")), 200);
            JsonNode award = awaitPublicStatus(credentialIds.get("AWARD"), "VALID", worker);
            assertSuiEvidence(award); assertPublicDownloads(credentialIds.get("AWARD")); emitPublicEvidence("VALID", award);
            String revokedId = credentialIds.get("PARTICIPATION");
            JsonNode statusApproval = admin(post("/api/admin/blockchain/credentials/" + revokedId + "/status-events"),
                    Map.of("action", "REVOKE", "issuerKeyVersion", keyVersion, "reasonCode", "SYNTHETIC_FULL_E2E",
                            "reasonDetail", "Synthetic participation test record only"), 201);
            persist("status-revoke.json", Map.of("credentialPublicId", revokedId, "statusEventId", statusApproval.path("aggregateId").asText()));
            admin(post("/api/admin/blockchain/status-events/" + statusApproval.path("aggregateId").asText() + "/approval"),
                    Map.of("signatureHex", sign(statusApproval, issuerKey, "StatusApproval")), 200);
            JsonNode revoked = awaitPublicStatus(revokedId, "REVOKED", worker);
            assertSuiEvidence(revoked); assertPublicDownloads(revokedId); emitPublicEvidence("REVOKED", revoked);
            require(chainTransactions.count() == 4 && batches.count() == 3 && statusEvents.count() == 1 && outbox.count() == 4,
                    "Resume must add exactly AWARD anchor plus PARTICIPATION revoke");
            for (var tx : chainTransactions.findAll()) {
                require(tx.getStatus() == ChainTransactionStatus.CONFIRMED && tx.getTxNonce() == null
                        && tx.getRelayerAddress().length == 32 && tx.getBlockNumber() != null && tx.getBlockHash().length == 32
                        && properties.chainContext().equals(tx.getChainContext()), "Every transaction must be confirmed in the same Sui context");
                String digest = SuiDigest.encode(tx.getTxHash());
                if (originalEnvelopes.containsKey(digest)) require(java.util.Arrays.equals(originalEnvelopes.get(digest), tx.getSignedRawTransaction()),
                        "Original signed bytes must remain unchanged");
                assertNoFixturePlaintext(tx.getSignedRawTransaction());
            }
            require(chainTransactions.findAll().stream().map(tx -> SuiDigest.encode(tx.getTxHash())).collect(java.util.stream.Collectors.toSet())
                    .containsAll(expectedOriginalDigests), "Original confirmed digests must remain present");
            for (var credential : credentials.findAll()) {
                require(java.util.Arrays.equals(originalCanonical.get(credential.getPublicId()), credential.getCanonicalBytes())
                        && java.util.Arrays.equals(originalContentHashes.get(credential.getPublicId()), credential.getContentHash()),
                        "Immutable credential bytes/hash must not change during recovery");
            }
            require(outbox.findAll().stream().allMatch(event -> event.getStatus() == OutboxStatus.PROCESSED), "All four outbox events must be processed");
            Map<String, Object> result = Map.of("status", "PASS", "database", "trekkey_sui_full", "resumed", true,
                    "preservedDigests", expectedOriginalDigests, "fixture", evidenceIds, "credentials", credentialIds, "transactions", publicTransactions());
            persist("result.json", result);
            System.out.println("SUI_FULL_RESULT " + JSON.writeValueAsString(result));
        } finally {
            for (var item : publicTransactions()) System.out.println("SUI_FULL_TRANSACTION " + JSON.writeValueAsString(item));
        }
    }

    private static JsonNode readState(String filename) throws Exception {
        return JSON.readTree(privateText(runDirectory.resolve(filename), 100_000));
    }

    private void rememberCredential(CredentialType type) {
        var matches = credentials.findAll().stream().filter(c -> c.getCredentialType() == type).toList();
        require(matches.size() == 1 && matches.getFirst().getStatus() == CredentialStatus.READY, "Exactly one new READY credential of the expected type required");
        credentialIds.put(type.name(), matches.getFirst().getPublicId());
    }

    private void assertPublicDownloads(String publicId) throws Exception {
        for (String suffix : List.of("package", "certificate")) {
            var download = mvc.perform(get("/api/public/credentials/" + publicId + "/" + suffix)).andReturn().getResponse();
            assertThat(download.getStatus()).isEqualTo(200);
            byte[] content = download.getContentAsByteArray();
            require(content.length > 100, "Public artifact is empty");
            require(suffix.equals("package") ? content[0] == 'P' && content[1] == 'K'
                    : new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF"), "Public artifact format is invalid");
        }
    }

    private List<Map<String, String>> publicTransactions() {
        return chainTransactions.findAll().stream().map(tx -> Map.of(
                "operation", tx.getOperationType().name(), "status", tx.getStatus().name(),
                "transactionDigest", tx.getTxHash() == null ? "not-prepared" : SuiDigest.encode(tx.getTxHash()),
                "checkpoint", tx.getBlockNumber() == null ? "pending" : tx.getBlockNumber().toString(),
                "errorCode", tx.getLastErrorCode() == null ? "" : tx.getLastErrorCode())).toList();
    }

    private static String privateText(Path path, int maxBytes) throws Exception {
        require(path.isAbsolute() && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= maxBytes,
                "Private input must be an absolute bounded regular file");
        ownerOnly(path);
        return Files.readString(path).strip();
    }

    private static void persist(String name, Object value) throws Exception {
        Path file = runDirectory.resolve(name);
        try (FileChannel channel = FileChannel.open(file, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer buffer = ByteBuffer.wrap(JSON.writeValueAsBytes(value));
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        try (FileChannel directory = FileChannel.open(runDirectory, StandardOpenOption.READ)) { directory.force(true); }
    }

    private JsonNode awaitPublicStatus(String publicId, String expected, BlockchainWorkers worker) throws Exception {
        Instant stop = Instant.now().plus(Duration.ofMinutes(3));
        String observed = "NOT_CHECKED";
        while (Instant.now().isBefore(stop)) {
            require(chainTransactions.count() <= 4 && approvedOperations <= 4, "Gas budget requires at most four signed operations");
            worker.submitPendingOutbox();
            worker.reconcileReceipts();
            for (var tx : chainTransactions.findAll()) {
                require(tx.getStatus() != ChainTransactionStatus.FAILED,
                        "E2E chain work failed with code " + tx.getLastErrorCode());
            }
            for (var event : outbox.findAll()) {
                require(event.getStatus() != OutboxStatus.DEAD, "E2E outbox failed with code " + event.getLastErrorCode());
            }
            JsonNode data = response(get("/api/public/credentials/" + publicId), 200);
            observed = data.path("verificationStatus").asText();
            CredentialStatus localExpected = expected.equals("VALID") ? CredentialStatus.ANCHORED : CredentialStatus.REVOKED;
            if (expected.equals(observed)
                    && credentials.findByPublicId(publicId).orElseThrow().getStatus() == localExpected
                    && chainTransactions.findAll().stream().allMatch(tx -> tx.getStatus() == ChainTransactionStatus.CONFIRMED)) {
                return data;
            }
            Thread.sleep(2_000);
        }
        throw new AssertionError("Testnet receipt/public verification timed out: expected=" + expected + ", observed=" + observed);
    }

    private JsonNode admin(MockHttpServletRequestBuilder request, Object body, int status) throws Exception {
        request.header("Authorization", "Bearer " + adminToken);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(body));
        return response(request, status);
    }

    private JsonNode response(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request).andReturn().getResponse();
        JsonNode body = JSON.readTree(result.getContentAsString());
        assertThat(result.getStatus()).withFailMessage("Unexpected HTTP status %s (public response code %s)",
                result.getStatus(), body.path("code").asText()).isEqualTo(expected);
        return body.path("data");
    }

    private String sign(JsonNode approval, ECKeyPair issuerKey, String primaryType) throws Exception {
        require(approvedOperations < 4, "No more than four issuer approvals may be signed");
        approvedOperations++;
        JsonNode payload = JSON.readTree(approval.path("typedDataJson").asText());
        assertApprovalContainsOnlyHashesAndNumbers(payload.path("message"), primaryType);
        assertThat(payload.path("scheme").asText()).isEqualTo(SuiApproval.SCHEME);
        assertThat(payload.path("primaryType").asText()).isEqualTo(primaryType);
        for (String field : List.of("chainIdentifier", "packageId", "registryId")) {
            assertThat(payload.path("domain").path(field).asText()).isEqualTo(text(manifest, field));
        }
        assertThat(payload.path("message").path("issuerId").asText()).isEqualTo(text(manifest, "issuerId"));
        assertThat(payload.path("digestHex").asText()).isEqualTo(approval.path("digestHex").asText());
        return Eip712.signDigest(Hash32.fromHex(approval.path("digestHex").asText()), issuerKey).hex();
    }

    /** Before issuer authorization, the on-chain approval ABI may contain no free-text data. */
    private void assertApprovalContainsOnlyHashesAndNumbers(JsonNode message, String primaryType) {
        Set<String> hashes = primaryType.equals("BatchApproval")
                ? Set.of("issuerId", "batchIdHash", "merkleRoot", "schemaVersionHash")
                : Set.of("issuerId", "credentialIdHash", "replacementCredentialIdHash");
        Set<String> numbers = primaryType.equals("BatchApproval")
                ? Set.of("leafCount", "treeVersion", "issuerKeyVersion", "approvalNonce", "deadline")
                : Set.of("action", "effectiveAt", "issuerKeyVersion", "approvalNonce", "deadline");
        Set<String> expected = new HashSet<>(hashes); expected.addAll(numbers);
        Set<String> actual = new HashSet<>(); message.fieldNames().forEachRemaining(actual::add);
        require(message.isObject() && actual.equals(expected), "Approval ABI must contain only the fixed hash/numeric fields");
        for (String field : hashes) require(message.path(field).asText().matches("0x[0-9a-f]{64}"), "Approval hash field is malformed");
        for (String field : numbers) require(message.path(field).asText().matches("[0-9]{1,20}"), "Approval unsigned numeric field is malformed");
    }

    /** Check actual persisted BCS transaction bytes, not their base64 text, for fixture plaintext. */
    private void assertNoFixturePlaintext(byte[] savedEnvelope) throws Exception {
        JsonNode envelope = JSON.readTree(savedEnvelope);
        require(envelope.path("version").asInt() == 1, "Unexpected signed transaction envelope");
        byte[] transactionBytes = Base64.getDecoder().decode(envelope.path("transactionBytes").asText());
        require(transactionBytes.length > 0, "Missing persisted BCS transaction");
        String binary = new String(transactionBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        for (String plaintext : fixturePlaintexts) require(!binary.contains(plaintext), "Fixture plaintext must never appear in on-chain transaction bytes");
    }

    private ECKeyPair syntheticIssuerKey() throws Exception {
        Path file = stateDirectory.resolve("synthetic-issuer.key");
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 100,
                "A bounded regular synthetic issuer key file is required");
        ownerOnly(file);
        String value = Files.readString(file).strip();
        // Never put the key in an assertion's actual value or an exception message.
        require(value.matches("0x[0-9a-fA-F]{64}"), "Synthetic issuer key file format is invalid");
        ECKeyPair pair = ECKeyPair.create(new BigInteger(value.substring(2), 16));
        require(("0x" + Keys.getAddress(pair)).equals(text(manifest, "signerAddress")),
                "Synthetic issuer key does not match the deployment's public signer address");
        return pair;
    }

    private void assertSuiEvidence(JsonNode result) {
        JsonNode evidence = result.path("evidence"), blockchain = evidence.path("blockchain");
        for (String check : List.of("canonicalPayloadMatches", "contentHashMatches", "fileManifestHashMatches",
                "credentialClaimsMatch", "credentialIdMatches", "merkleProofMatches")) {
            assertThat(evidence.path(check).asBoolean()).as(check).isTrue();
        }
        assertThat(evidence.path("chainId").asLong()).isZero();
        assertThat(blockchain.path("provider").asText()).isEqualTo("SUI");
        assertThat(blockchain.path("network").asText()).isEqualTo("testnet");
        assertThat(blockchain.path("packageId").asText()).isEqualTo(text(manifest, "packageId"));
        assertThat(blockchain.path("registryObjectId").asText()).isEqualTo(text(manifest, "registryId"));
        assertThat(blockchain.path("transactionDigest").asText()).isNotBlank();
        assertThat(blockchain.path("checkpointDigest").asText()).isNotBlank();
    }

    private void emitPublicEvidence(String phase, JsonNode data) throws Exception {
        System.out.println("SUI_FULL_PUBLIC_" + phase + " " + JSON.writeValueAsString(Map.of(
                "credentialPublicId", data.path("credentialPublicId").asText(), "verificationStatus", data.path("verificationStatus").asText(),
                "blockchain", data.path("evidence").path("blockchain"))));
    }

    private static void ownerOnly(Path path) throws Exception {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        require(permissions.equals(PosixFilePermissions.fromString(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? "rwx------" : "rw-------")),
                "Full E2E private directories/files must be exactly 0700/0600");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(), "Required E2E environment variable is missing: " + name);
        return value;
    }

    private static String text(JsonNode node, String field) {
        require(node.path(field).isTextual() && !node.path(field).asText().isBlank(), "Manifest field is missing: " + field);
        return node.path(field).asText();
    }

    private static void requireGate() {
        require("true".equals(System.getenv("SUI_FULL_E2E")), "Actual testnet calls require explicit SUI_FULL_E2E=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
