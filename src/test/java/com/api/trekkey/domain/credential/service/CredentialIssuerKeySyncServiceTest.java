package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.credential.service.dto.IssuerKeyView;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.support.ApprovalNonceGenerator;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CredentialIssuerKeySyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T03:00:00Z");
    private static final String ORGANIZATION_PUBLIC_ID = "organization-public-1";
    private static final EthereumAddress SIGNER =
            EthereumAddress.fromHex("0x1111111111111111111111111111111111111111");

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AncIssuerKeyRepository issuerKeyRepository;
    @Mock private AncCredentialRepository credentialRepository;
    @Mock private AncBatchRepository batchRepository;
    @Mock private AncBatchItemRepository batchItemRepository;
    @Mock private AncCredentialStatusEventRepository statusEventRepository;
    @Mock private AncChainTransactionRepository chainTransactionRepository;
    @Mock private AncOutboxEventRepository outboxEventRepository;
    @Mock private BlockchainAnchorPort blockchainAnchorPort;
    @Mock private ApprovalNonceGenerator nonceGenerator;

    private CredentialBlockchainServiceImpl service;

    @BeforeEach
    void setUp() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.READ_ONLY);
        properties.setContractAddress("0x2222222222222222222222222222222222222222");
        service = new CredentialBlockchainServiceImpl(
                organizationRepository,
                userRepository,
                issuerKeyRepository,
                credentialRepository,
                batchRepository,
                batchItemRepository,
                statusEventRepository,
                chainTransactionRepository,
                outboxEventRepository,
                blockchainAnchorPort,
                properties,
                nonceGenerator,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void synchronizesRetirementAndCompromiseFromTheOnChainKeyLedger() {
        Organization organization = organization();
        long validFrom = NOW.minusSeconds(3600).getEpochSecond();
        long validUntil = NOW.minusSeconds(1200).getEpochSecond();
        long compromisedAt = NOW.minusSeconds(600).getEpochSecond();
        given(organizationRepository.findByIdForUpdate(1L)).willReturn(Optional.of(organization));
        given(blockchainAnchorPort.getIssuerKey(Hashing.issuerId(ORGANIZATION_PUBLIC_ID), 1))
                .willReturn(new BlockchainAnchorPort.OnChainIssuerKey(
                        SIGNER,
                        validFrom,
                        validUntil,
                        compromisedAt,
                        true));
        given(issuerKeyRepository.findByOrganizationIdAndKeyVersion(1L, 1)).willReturn(Optional.empty());
        given(issuerKeyRepository.save(any(AncIssuerKey.class))).willAnswer(invocation -> {
            AncIssuerKey key = invocation.getArgument(0);
            ReflectionTestUtils.setField(key, "id", 5L);
            return key;
        });

        IssuerKeyView result = service.syncIssuerKey(1L, 1, "school-kms:key-1");

        assertThat(result.status()).isEqualTo(IssuerKeyStatus.COMPROMISED);
        assertThat(result.validUntil()).isEqualTo(Instant.ofEpochSecond(validUntil));
        assertThat(result.compromisedAt()).isEqualTo(Instant.ofEpochSecond(compromisedAt));
        then(organizationRepository).should().findByIdForUpdate(1L);
    }

    private Organization organization() {
        Organization value = org.springframework.beans.BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(value, "id", 1L);
        ReflectionTestUtils.setField(value, "publicId", ORGANIZATION_PUBLIC_ID);
        return value;
    }
}
