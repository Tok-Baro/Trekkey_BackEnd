package com.api.trekkey.domain.credential.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import com.api.trekkey.domain.credential.service.CredentialBlockchainService;
import com.api.trekkey.domain.credential.service.dto.IssuerKeyView;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Opt-in Kairos read smoke test. It proves that the production Spring adapter can resolve an
 * on-chain issuer from the same public UUID persisted on {@link Organization}, without loading a
 * relayer private key.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kairos-read-only;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.task.scheduling.enabled=false",
        "security.jwt.secret-key=c2VjdXJpdHktcmVhZC1vbmx5LXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "blockchain.anchoring.mode=READ_ONLY",
        "blockchain.anchoring.chain-id=1001",
        "blockchain.anchoring.runtime-code-hash=0x6bdcd078a99c833e1e6126d71954b570fb7bfb4afe4720fc4a438009039571a2",
        "blockchain.anchoring.contract-version=1",
        "blockchain.anchoring.tree-version=1",
        "blockchain.anchoring.worker-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "KAIROS_READ_ONLY_LIVE", matches = "true")
class KairosReadOnlyLiveTest {

    private static final String DEFAULT_RPC_URL = "https://public-en-kairos.node.kaia.io";
    private static final String DEFAULT_CONTRACT_ADDRESS = "0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117";

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CredentialBlockchainService credentialBlockchainService;

    @DynamicPropertySource
    static void blockchainProperties(DynamicPropertyRegistry registry) {
        registry.add("blockchain.anchoring.rpc-url", () -> environmentOrDefault(
                "KAIROS_E2E_RPC_URL", DEFAULT_RPC_URL));
        registry.add("blockchain.anchoring.contract-address", () -> environmentOrDefault(
                "KAIROS_E2E_CONTRACT_ADDRESS", DEFAULT_CONTRACT_ADDRESS));
    }

    @Test
    @Timeout(90)
    @DisplayName("READ_ONLY 백엔드가 Organization publicId로 Kairos issuer signer를 조회한다")
    void resolvesIssuerFromPersistedOrganizationPublicId() {
        String publicId = requiredEnvironment("KAIROS_E2E_ORGANIZATION_PUBLIC_ID");
        UUID.fromString(publicId);
        int keyVersion = Integer.parseInt(requiredEnvironment("KAIROS_E2E_ISSUER_KEY_VERSION"));
        String expectedSigner = requiredEnvironment("KAIROS_E2E_ISSUER_SIGNER_ADDRESS");

        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "publicId", publicId);
        ReflectionTestUtils.setField(organization, "name", "Kairos Read-Only Organization");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        organization = organizationRepository.saveAndFlush(organization);

        assertThat(organization.getPublicId()).isEqualTo(publicId);
        IssuerKeyView issuerKey = credentialBlockchainService.syncIssuerKey(
                organization.getId(), keyVersion, "kairos-read-only-smoke");

        assertThat(issuerKey.keyVersion()).isEqualTo(keyVersion);
        assertThat(issuerKey.status()).isEqualTo(IssuerKeyStatus.ACTIVE);
        assertThat(issuerKey.signerAddress()).isEqualToIgnoringCase(expectedSigner);
        assertThat(System.getenv("KAIROS_E2E_RELAYER_PRIVATE_KEY")).isNull();
        System.out.printf(
                Locale.ROOT,
                "KAIROS_READ_ONLY organizationPublicId=%s issuerKeyVersion=%d signer=%s%n",
                organization.getPublicId(),
                issuerKey.keyVersion(),
                issuerKey.signerAddress());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when KAIROS_READ_ONLY_LIVE=true");
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
