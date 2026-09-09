package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncBatchItem;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.BatchStatus;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.dto.StatusChangeCommand;
import com.api.trekkey.domain.credential.service.support.ApprovalNonceGenerator;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class SuiMigrationGuardTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final AncIssuerKeyRepository issuerKeys = mock(AncIssuerKeyRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AncCredentialRepository credentials = mock(AncCredentialRepository.class);
    private final AncBatchItemRepository batchItems = mock(AncBatchItemRepository.class);
    private final AncCredentialStatusEventRepository statusEvents = mock(AncCredentialStatusEventRepository.class);
    private final AncBatchRepository batches = mock(AncBatchRepository.class);
    private final AncChainTransactionRepository transactions = mock(AncChainTransactionRepository.class);
    private final AncOutboxEventRepository outbox = mock(AncOutboxEventRepository.class);
    private final BlockchainAnchorPort chain = mock(BlockchainAnchorPort.class);
    private final ApprovalNonceGenerator nonces = mock(ApprovalNonceGenerator.class);

    @Test
    void legacyUnsignedBatchCannotBeApprovedOrReissuedUnderSuiConfiguration() {
        AncBatch legacy = batch();
        given(batches.findByPublicId("legacy-batch")).willReturn(Optional.of(legacy));
        given(batches.findByPublicIdForUpdate("legacy-batch")).willReturn(Optional.of(legacy));
        CredentialBlockchainServiceImpl service = service(sui());

        rejectsContext(() -> service.getBatchApproval(1L, "legacy-batch"));
        rejectsContext(() -> service.approveBatch(1L, "legacy-batch", "must-not-parse-this-signature"));
        rejectsContext(() -> service.renewBatchApproval(1L, "legacy-batch"));
        rejectsContext(() -> service.reconcileBatch(1L, "legacy-batch"));

        assertThat(legacy.getStatus()).isEqualTo(BatchStatus.SEALED);
        assertThat(legacy.getIssuerSignature()).isNull();
        assertThat(legacy.getApprovalDigest()).isNull();
        assertThat(legacy.getChainContext()).isNull();
        verifyNoInteractions(organizations, issuerKeys, transactions, outbox, chain, nonces);
    }

    @Test
    void explicitlyKaiaBoundBatchIsNotReinterpretedAsSuiEvenBeforeSigning() {
        BlockchainProperties kaia = new BlockchainProperties();
        kaia.setContractAddress("0x" + "11".repeat(20));
        AncBatch legacy = batch().inContext(kaia.chainContext());
        given(batches.findByPublicIdForUpdate("legacy-batch")).willReturn(Optional.of(legacy));
        rejectsContext(() -> service(sui()).approveBatch(1L, "legacy-batch", "must-not-parse-this-signature"));
        assertThat(legacy.getChainContext()).isEqualTo(kaia.chainContext());
        assertThat(legacy.getStatus()).isEqualTo(BatchStatus.SEALED);
        verifyNoInteractions(organizations, issuerKeys, transactions, outbox, chain, nonces);
    }

    @Test
    void providerNetworkPackageRegistryAndVersionSwitchesBlockExistingSuiBatchBeforeSideEffects() {
        BlockchainProperties original = sui();
        AncBatch batch = batch().inContext(original.chainContext());
        given(batches.findByPublicIdForUpdate("legacy-batch")).willReturn(Optional.of(batch));
        List<Consumer<BlockchainProperties>> mutations = List.of(
            p -> { p.setProvider(BlockchainProperties.Provider.KAIA); p.setContractAddress("0x" + "11".repeat(20)); },
            p -> p.getSui().setNetwork("localnet"),
            p -> p.getSui().setChainIdentifier("a1b2c3d5"),
            p -> p.getSui().setPackageId("0x" + "33".repeat(32)),
            p -> p.getSui().setRegistryId("0x" + "44".repeat(32)),
            p -> p.setContractVersion("2")
        );
        for (Consumer<BlockchainProperties> mutation : mutations) {
            BlockchainProperties changed = sui();
            mutation.accept(changed);
            rejectsContext(() -> service(changed).approveBatch(1L, "legacy-batch", "must-not-parse-this-signature"));
        }
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.SEALED);
        assertThat(batch.getChainContext()).isEqualTo(original.chainContext());
        verifyNoInteractions(organizations, issuerKeys, transactions, outbox, chain, nonces);
    }

    @Test
    void organizationBoundaryIsCheckedBeforeExposingAContextMismatch() {
        given(batches.findByPublicIdForUpdate("legacy-batch")).willReturn(Optional.of(batch()));
        assertThatThrownBy(() -> service(sui()).approveBatch(2L, "legacy-batch", "unused"))
            .isInstanceOf(CustomException.class)
            .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode())
                .isEqualTo(CredentialErrorResponseCode.BATCH_NOT_FOUND));
        verifyNoInteractions(organizations, issuerKeys, transactions, outbox, chain, nonces);
    }

    @Test
    void missingSuiIssuerVersionFailsExplicitlyBeforeClaimingReadyCredentials() {
        Organization organization = organization();
        given(organizations.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeys.findByOrganizationIdAndKeyVersion(1L, 2)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service(sui()).sealBatch(1L, CredentialSchemaProfiles.AWARD_V1, 2))
            .isInstanceOf(CustomException.class)
            .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode())
                .isEqualTo(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));

        verifyNoInteractions(credentials, batches, batchItems, transactions, outbox, chain, nonces);
        verify(issuerKeys, never()).save(any());
    }

    @Test
    void legacyIssuerVersionCannotSealNewSuiBatchOrChangeItsLifecycle() {
        AncIssuerKey legacy = legacyKey();
        Organization organization = organization();
        given(organizations.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeys.findByOrganizationIdAndKeyVersion(1L, 1)).willReturn(Optional.of(legacy));

        rejectsContext(() -> service(sui()).sealBatch(1L, CredentialSchemaProfiles.AWARD_V1, 1));

        assertThat(legacy.getChainContext()).isNull();
        assertThat(legacy.getStatus()).isEqualTo(IssuerKeyStatus.ACTIVE);
        assertThat(legacy.getValidUntil()).isNull();
        verifyNoInteractions(credentials, batches, batchItems, transactions, outbox, chain, nonces);
        verify(issuerKeys, never()).save(any());
    }

    @Test
    void unregisteredOnChainSuiIssuerCannotBeSynthesizedByKeySync() {
        Organization organization = organization();
        given(organizations.findByIdForUpdate(1L)).willReturn(Optional.of(organization));
        given(chain.getIssuerKey(Hashing.issuerId("institution-public-id"), 2))
            .willReturn(new BlockchainAnchorPort.OnChainIssuerKey(null, 0, 0, 0, false));

        assertThatThrownBy(() -> service(sui()).syncIssuerKey(1L, 2, "external:real-institution"))
            .isInstanceOf(CustomException.class)
            .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode())
                .isEqualTo(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));

        verifyNoInteractions(issuerKeys, credentials, batches, batchItems, transactions, outbox, nonces);
    }

    @Test
    void suiKeySyncNeverOverwritesLegacyKeyEvenWhenSameVersionExistsOnSui() {
        AncIssuerKey legacy = legacyKey();
        Organization organization = organization();
        given(organizations.findByIdForUpdate(1L)).willReturn(Optional.of(organization));
        given(issuerKeys.findByOrganizationIdAndKeyVersion(1L, 1)).willReturn(Optional.of(legacy));
        given(chain.getIssuerKey(Hashing.issuerId("institution-public-id"), 1))
            .willReturn(new BlockchainAnchorPort.OnChainIssuerKey(
                EthereumAddress.fromHex("0x" + "11".repeat(20)),
                NOW.minusDays(1).toEpochSecond(ZoneOffset.UTC), NOW.toEpochSecond(ZoneOffset.UTC), 0, true));

        rejectsContext(() -> service(sui()).syncIssuerKey(1L, 1, "external:real-institution"));

        assertThat(legacy.getChainContext()).isNull();
        assertThat(legacy.getSignerRef()).isEqualTo("external:legacy-institution");
        assertThat(legacy.getStatus()).isEqualTo(IssuerKeyStatus.ACTIVE);
        assertThat(legacy.getValidUntil()).isNull();
        verify(issuerKeys, never()).save(any());
        verifyNoInteractions(credentials, batches, batchItems, transactions, outbox, nonces);
    }

    @Test
    void legacyAwardRevocationIsBlockedBeforeCreatingAnyStatusApprovalWithSuiRelayer() {
        Organization organization = organization();
        User actor = mock(User.class);
        given(actor.getOrganization()).willReturn(organization);
        given(users.findById(9L)).willReturn(Optional.of(actor));
        AncCredential credential = mock(AncCredential.class);
        given(credential.getId()).willReturn(10L);
        given(credential.getIssuerOrganizationId()).willReturn(1L);
        given(credential.getStatus()).willReturn(CredentialStatus.ANCHORED);
        given(credentials.findByPublicIdForUpdate("legacy-award")).willReturn(Optional.of(credential));
        given(batchItems.findByCredentialId(10L))
            .willReturn(Optional.of(AncBatchItem.of(7L, 10L, 0, new byte[32], new byte[32], "[]")));

        for (boolean migrated : List.of(false, true)) {
            AncBatch legacy = batch();
            if (migrated) legacy.inContext("KAIA|1001|0x" + "11".repeat(20) + "|1");
            given(batches.findById(7L)).willReturn(Optional.of(legacy));
            rejectsContext(() -> service(sui()).requestStatusChange(1L, 9L, "legacy-award",
                new StatusChangeCommand(StatusChangeCommand.Action.REVOKE, null, 2, "ISSUED_IN_ERROR", null)));
            assertThat(legacy.getStatus()).isEqualTo(BatchStatus.SEALED);
        }

        verify(statusEvents, never()).save(any());
        verifyNoInteractions(issuerKeys, transactions, outbox, chain, nonces);
    }

    private static void rejectsContext(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(CustomException.class)
            .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode())
                .isEqualTo(CredentialErrorResponseCode.BLOCKCHAIN_CONFIGURATION_INVALID));
    }

    private CredentialBlockchainServiceImpl service(BlockchainProperties properties) {
        return new CredentialBlockchainServiceImpl(organizations, users, issuerKeys,
            credentials, batches, batchItems,
            statusEvents, transactions, outbox, chain, properties,
            nonces, new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC));
    }

    private static AncBatch batch() {
        return AncBatch.seal(1L, 2L, "legacy-batch", new byte[32], new byte[32],
            1, 1, new byte[32], 1L, NOW.plusMinutes(15), NOW);
    }

    private static Organization organization() {
        Organization organization = mock(Organization.class);
        given(organization.getId()).willReturn(1L);
        given(organization.ensurePublicId()).willReturn("institution-public-id");
        return organization;
    }

    private static AncIssuerKey legacyKey() {
        return AncIssuerKey.activate(1L, 1, EthereumAddress.fromHex("0x" + "11".repeat(20)).bytes(),
            "external:legacy-institution", NOW.minusDays(1), null);
    }

    private static BlockchainProperties sui() {
        BlockchainProperties properties = new BlockchainProperties();
        properties.setProvider(BlockchainProperties.Provider.SUI);
        properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
        properties.getSui().setChainIdentifier("a1b2c3d4");
        properties.getSui().setPackageId("0x" + "11".repeat(32));
        properties.getSui().setRegistryId("0x" + "22".repeat(32));
        properties.getSui().setGatewayToken("a".repeat(32));
        return properties;
    }
}
