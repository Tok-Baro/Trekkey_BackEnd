package com.api.trekkey.domain.credential.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.crypto.SuiApproval;
import com.api.trekkey.domain.credential.crypto.SuiDigest;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.credential.service.CredentialIssuanceService;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
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
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

/**
 * Opt-in, synthetic-only testnet writes: one anchor and one revocation, through the real Java
 * services/outbox/HTTP gateway. Never part of an ordinary test run. No real database is accessed.
 *
 * Required only for an operator-approved execution: SUI_TESTNET_E2E=true,
 * SUI_E2E_STATE_DIR (deployment.json + owner-only synthetic-issuer.key), SUI_GATEWAY_TOKEN_FILE
 * (owner-only token file; SUI_GATEWAY_TOKEN is a fallback for an explicitly isolated local run).
 * Optional SUI_E2E_GATEWAY_URL must be a loopback origin (default http://127.0.0.1:9187).
 * The remote gateway's durable journal must be retained even if this ephemeral H2 test fails.
 * Logs contain only synthetic public IDs, verification evidence and transaction coordinates.
 */
@EnabledIfEnvironmentVariable(named = "SUI_TESTNET_E2E", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "security.jwt.secret-key=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==",
        "security.jwt.access-expiration=3600", "security.jwt.refresh-expiration=7200",
        "app.cors.allowed-origin=http://localhost:3000", "app.front.base-url=http://localhost:3000",
        "app.file.upload-dir=${java.io.tmpdir}/trekkey-sui-e2e-unused-upload",
        "blockchain.anchoring.provider=SUI", "blockchain.anchoring.mode=LOCAL_RELAYER",
        "blockchain.anchoring.worker-enabled=false", "blockchain.anchoring.worker-claim-size=1",
        "blockchain.anchoring.sui.network=testnet", "blockchain.anchoring.chain-id=0",
        "blockchain.anchoring.contract-version=1", "blockchain.anchoring.approval-ttl=15m",
        "blockchain.anchoring.sui.request-timeout=60s", "blockchain.anchoring.receipt-timeout=60s"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(SuiTestnetCredentialLifecycleIntegrationTest.IsolatedDatabase.class)
class SuiTestnetCredentialLifecycleIntegrationTest {
    private static final String H2_URL = "jdbc:h2:mem:sui-testnet-e2e-" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode manifest;
    private static Path stateDirectory;

    @TestConfiguration(proxyBeanMethods = false)
    static class IsolatedDatabase {
        @Bean
        DataSource dataSource() {
            requireGate();
            // Do not let vendor-specific datasource properties (for example an inherited
            // SPRING_DATASOURCE_HIKARI_JDBC_URL) route Hibernate's create-drop to another DB.
            DriverManagerDataSource source = new DriverManagerDataSource(H2_URL, "sa", "");
            source.setDriverClassName("org.h2.Driver");
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
        registry.add("spring.datasource.url", () -> H2_URL);
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
    @Autowired CredentialIssuanceService issuance;
    @Autowired AncCredentialRepository credentials;
    @Autowired AncChainTransactionRepository chainTransactions;
    @Autowired AncOutboxEventRepository outbox;
    @Autowired BlockchainWorkTransactions workTransactions;
    @Autowired BlockchainAnchorPort anchor;
    @Autowired BlockchainProperties properties;
    @Autowired Clock credentialClock;
    @Autowired JwtTokenProvider tokens;
    private String adminToken;

    @Test
    void syntheticCredentialAnchorsAsValidThenRevokesThroughTheRealOutboxAndPublicApi() throws Exception {
        requireGate();
        try (var connection = dataSource.getConnection()) {
            require(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:sui-testnet-e2e-"),
                    "Testnet E2E refuses any non-isolated database");
        }
        assertThat(properties.isWorkerEnabled()).isFalse();
        assertThat(properties.getProvider()).isEqualTo(BlockchainProperties.Provider.SUI);
        assertThat(properties.getSui().getNetwork()).isEqualTo("testnet");
        ECKeyPair issuerKey = syntheticIssuerKey();
        Organization org = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(org, "publicId", text(manifest, "organizationPublicId"));
        ReflectionTestUtils.setField(org, "name", "SYNTHETIC Trekkey Sui E2E — NOT A REAL INSTITUTION");
        ReflectionTestUtils.setField(org, "status", OrganizationStatus.ACTIVE);
        org = organizations.saveAndFlush(org);
        User admin = users.saveAndFlush(User.builder().organization(org).name("Synthetic E2E Admin")
                .email("sui-e2e-" + UUID.randomUUID() + "@example.invalid").password("disabled-synthetic-account")
                .role(UserRole.ADMIN).memberType(MemberType.STAFF).status(UserStatus.ACTIVE).build());
        AuthPrincipal principal = AuthPrincipal.of(admin.getId(), admin.getEmail(), List.of("ADMIN"));
        adminToken = tokens.createAccessToken(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        // Automatic scheduled workers keep their real configuration disabled. Only this manually
        // driven instance runs, using the real proxied ledger transactions and real gateway adapter.
        BlockchainProperties manual = new BlockchainProperties();
        BeanUtils.copyProperties(properties, manual);
        manual.setWorkerEnabled(true);
        BlockchainWorkers worker = new BlockchainWorkers(workTransactions, anchor, manual, credentialClock);
        String publicId = null;
        try {
            int keyVersion = manifest.path("keyVersion").asInt();
            JsonNode synced = admin(post("/api/admin/blockchain/issuer-keys/" + keyVersion + "/sync"),
                    Map.of("signerRef", "synthetic-e2e-external-issuer"), 201);
            assertThat(synced.path("signerAddress").asText()).isEqualTo(text(manifest, "signerAddress"));
            Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5);
            publicId = issuance.issue(new CredentialIssueCommand(org.getId(), "SUI-E2E-" + UUID.randomUUID(),
                    CredentialType.AWARD, CredentialSchemaProfiles.AWARD_V1,
                    new CredentialIssueCommand.Source(CredentialSourceType.AWARD, null, null, 1L,
                            "synthetic-award-" + UUID.randomUUID(), issuedAt.minusSeconds(1),
                            JSON.createObjectNode().put("synthetic", true).put("prize", "SYNTHETIC TEST ONLY")),
                    List.of(new CredentialIssueCommand.Subject(null, 1L, "synthetic-team", CredentialSubjectType.TEAM,
                                    "Synthetic Test Team", null, "AWARDEE", DisclosureClass.PUBLIC, 0),
                            new CredentialIssueCommand.Subject(admin.getId(), null, "synthetic-user", CredentialSubjectType.USER,
                                    "Synthetic Test Person", "Synthetic", "REPRESENTATIVE", DisclosureClass.PUBLIC, 1)),
                    List.of(), issuedAt, null)).publicId();
            assertThat(credentials.findByPublicId(publicId).orElseThrow().getStatus()).isEqualTo(CredentialStatus.READY);
            String batchId = admin(post("/api/admin/blockchain/batches"),
                    Map.of("schemaProfileId", CredentialSchemaProfiles.AWARD_V1, "keyVersion", keyVersion), 201).path("publicId").asText();
            JsonNode batchApproval = admin(get("/api/admin/blockchain/batches/" + batchId + "/approval"), null, 200);
            admin(post("/api/admin/blockchain/batches/" + batchId + "/approval"),
                    Map.of("signatureHex", sign(batchApproval, issuerKey, "BatchApproval")), 200);
            assertThat(chainTransactions.findAll()).hasSize(1);
            assertThat(chainTransactions.findAll().getFirst().getStatus()).isEqualTo(ChainTransactionStatus.PENDING);
            JsonNode valid = awaitPublicStatus(publicId, "VALID", worker);
            assertSuiEvidence(valid);
            emitPublicEvidence("VALID", valid);

            JsonNode statusApproval = admin(post("/api/admin/blockchain/credentials/" + publicId + "/status-events"),
                    Map.of("action", "REVOKE", "issuerKeyVersion", keyVersion, "reasonCode", "SYNTHETIC_E2E",
                            "reasonDetail", "Synthetic test credential only; not a real academic record"), 201);
            admin(post("/api/admin/blockchain/status-events/" + statusApproval.path("aggregateId").asText() + "/approval"),
                    Map.of("signatureHex", sign(statusApproval, issuerKey, "StatusApproval")), 200);
            JsonNode revoked = awaitPublicStatus(publicId, "REVOKED", worker);
            assertSuiEvidence(revoked);
            assertThat(credentials.findByPublicId(publicId).orElseThrow().getStatus()).isEqualTo(CredentialStatus.REVOKED);
            assertThat(chainTransactions.findAll()).hasSize(2).allSatisfy(tx -> {
                assertThat(tx.getStatus()).isEqualTo(ChainTransactionStatus.CONFIRMED);
                assertThat(tx.getChainId()).isZero();
                assertThat(tx.getTxNonce()).isNull();
                assertThat(tx.getRelayerAddress()).hasSize(32);
                assertThat(tx.getSignedRawTransaction()).isNotEmpty();
                assertThat(tx.getBlockNumber()).isNotNull();
                assertThat(tx.getBlockHash()).hasSize(32);
            });
            assertThat(chainTransactions.findAll()).extracting(tx -> tx.getOperationType())
                    .containsExactlyInAnyOrder(ChainOperationType.ANCHOR_BATCH, ChainOperationType.REVOKE);
            emitPublicEvidence("REVOKED", revoked);
        } finally {
            // Public receipt IDs survive in the test report even on failure. Never log signed raw
            // envelopes, authorization headers, JWTs, issuer private keys or the gateway token.
            for (var tx : chainTransactions.findAll()) {
                System.out.println("SUI_E2E_TRANSACTION " + JSON.writeValueAsString(Map.of(
                        "credentialPublicId", publicId == null ? "not-issued" : publicId,
                        "operation", tx.getOperationType().name(), "status", tx.getStatus().name(),
                        "transactionDigest", tx.getTxHash() == null ? "not-prepared" : SuiDigest.encode(tx.getTxHash()),
                        "checkpoint", tx.getBlockNumber() == null ? "pending" : tx.getBlockNumber().toString(),
                        "errorCode", tx.getLastErrorCode() == null ? "" : tx.getLastErrorCode())));
            }
        }
    }

    private JsonNode awaitPublicStatus(String publicId, String expected, BlockchainWorkers worker) throws Exception {
        Instant stop = Instant.now().plus(Duration.ofMinutes(3));
        String observed = "NOT_CHECKED";
        while (Instant.now().isBefore(stop)) {
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
        JsonNode payload = JSON.readTree(approval.path("typedDataJson").asText());
        assertThat(payload.path("scheme").asText()).isEqualTo(SuiApproval.SCHEME);
        assertThat(payload.path("primaryType").asText()).isEqualTo(primaryType);
        for (String field : List.of("chainIdentifier", "packageId", "registryId")) {
            assertThat(payload.path("domain").path(field).asText()).isEqualTo(text(manifest, field));
        }
        assertThat(payload.path("message").path("issuerId").asText()).isEqualTo(text(manifest, "issuerId"));
        assertThat(payload.path("digestHex").asText()).isEqualTo(approval.path("digestHex").asText());
        return Eip712.signDigest(Hash32.fromHex(approval.path("digestHex").asText()), issuerKey).hex();
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
        System.out.println("SUI_E2E_PUBLIC_" + phase + " " + JSON.writeValueAsString(data));
    }

    private static void ownerOnly(Path path) throws Exception {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        require(permissions.stream().noneMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")),
                "Synthetic E2E state and issuer key must be owner-only");
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
        require("true".equals(System.getenv("SUI_TESTNET_E2E")), "Actual testnet calls require explicit SUI_TESTNET_E2E=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
