package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncBatchItem;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialStatusEvent;
import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.AncOutboxEvent;
import com.api.trekkey.domain.credential.entity.BatchStatus;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.OutboxAggregateType;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.credential.service.dto.SealedBatchView;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.support.ApprovalNonceGenerator;
import com.api.trekkey.domain.credential.service.support.ApprovalPayloadFactory;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.ECKeyPair;

@ExtendWith(MockitoExtension.class)
class CredentialBlockchainServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-24T03:00:00Z");
    private static final String CONTRACT = "0x1111111111111111111111111111111111111111";

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
    private BlockchainProperties properties;
    private Organization organization;
    private AncIssuerKey issuerKey;
    private AncCredential credential;
    private ECKeyPair keyPair;
    private AtomicReference<AncBatch> savedBatch;

    @BeforeEach
    void setUp() {
        properties = new BlockchainProperties();
        properties.setMode(BlockchainProperties.Mode.LOCAL_RELAYER);
        properties.setChainId(1001L);
        properties.setContractAddress(CONTRACT);
        properties.setApprovalTtl(Duration.ofMinutes(15));
        properties.setBatchSize(100);
        properties.setTreeVersion(1);
        keyPair = ECKeyPair.create(new BigInteger(
                "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
                16));
        organization = organization();
        issuerKey = issuerKey();
        credential = credential();
        savedBatch = new AtomicReference<>();

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
    void sealsMerkleBatchAndAcceptsOnlyTheRegisteredIssuerSignature() {
        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeyRepository.findByOrganizationIdAndKeyVersion(1L, 1)).willReturn(Optional.of(issuerKey));
        given(issuerKeyRepository.findById(5L)).willReturn(Optional.of(issuerKey));
        given(nonceGenerator.next()).willReturn(7L);
        given(credentialRepository.findBatchClaimCandidatesForUpdate(
                eq(1L),
                any(byte[].class),
                eq(CredentialStatus.READY),
                any())).willReturn(List.of(credential));
        given(batchRepository.save(any(AncBatch.class))).willAnswer(invocation -> {
            AncBatch batch = invocation.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 20L);
            savedBatch.set(batch);
            return batch;
        });

        SealedBatchView sealed = service.sealBatch(1L, CredentialSchemaProfiles.AWARD_V1, 1);
        AncBatch batch = savedBatch.get();

        assertThat(sealed.status()).isEqualTo(BatchStatus.SEALED);
        assertThat(sealed.leafCount()).isEqualTo(1);
        assertThat(credential.getStatus()).isEqualTo(CredentialStatus.BATCHED);
        assertThat(batch.getMerkleRoot()).hasSize(32);
        verify(batchItemRepository).saveAll(any());

        given(batchRepository.findByPublicIdForUpdate(batch.getPublicId())).willReturn(Optional.of(batch));
        given(chainTransactionRepository.save(any(AncChainTransaction.class))).willAnswer(invocation -> {
            AncChainTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 30L);
            return transaction;
        });
        given(outboxEventRepository.save(any(AncOutboxEvent.class))).willAnswer(invocation -> invocation.getArgument(0));
        Eip712.BatchApproval approval =
                ApprovalPayloadFactory.batch(organization.getPublicId(), batch, issuerKey);
        String signature = Eip712.signDigest(
                Eip712.batchDigest(ApprovalPayloadFactory.domain(properties), approval),
                keyPair).hex();

        SealedBatchView approved = service.approveBatch(1L, batch.getPublicId(), signature);

        assertThat(approved.status()).isEqualTo(BatchStatus.SIGNED);
        assertThat(batch.getApprovalDigest()).hasSize(32);
        assertThat(batch.getIssuerSignature()).hasSize(65);
        verify(chainTransactionRepository).save(any(AncChainTransaction.class));
        verify(outboxEventRepository).save(any(AncOutboxEvent.class));
    }

    @Test
    void renewsAnExpiredUnsignedBatchWithAFreshNonceAndDeadline() {
        AncBatch batch = AncBatch.seal(
                1L, 5L, "expired-batch", Hashing.keccak256Utf8("expired-batch").bytes(),
                Hashing.schemaVersion(CredentialSchemaProfiles.AWARD_V1).bytes(), 1, 1,
                Hashing.keccak256Utf8("root").bytes(), 1,
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                LocalDateTime.ofInstant(NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));
        ReflectionTestUtils.setField(batch, "id", 21L);
        given(batchRepository.findByPublicIdForUpdate("expired-batch")).willReturn(Optional.of(batch));
        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeyRepository.findById(5L)).willReturn(Optional.of(issuerKey));
        given(nonceGenerator.next()).willReturn(2L);

        var approval = service.renewBatchApproval(1L, "expired-batch");

        assertThat(approval.aggregateId()).isEqualTo("expired-batch");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.SEALED);
        assertThat(batch.getApprovalNonce()).isEqualTo(2L);
        assertThat(batch.getApprovalDeadline()).isEqualTo(
                LocalDateTime.ofInstant(NOW.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
        assertThat(batch.getApprovalDigest()).isNull();
        assertThat(batch.getIssuerSignature()).isNull();
    }

    @Test
    void rejectsRenewalWhenAFailedBatchAlreadyExistsOnChain() {
        AncBatch batch = failedBatch();
        AncChainTransaction transaction = failedBatchTransaction();
        AncOutboxEvent outbox = deadBatchOutbox();
        given(batchRepository.findByPublicIdForUpdate(batch.getPublicId())).willReturn(Optional.of(batch));
        given(chainTransactionRepository.findByBatchIdAndOperationType(batch.getId(), ChainOperationType.ANCHOR_BATCH))
                .willReturn(Optional.of(transaction));
        given(outboxEventRepository.findByIdempotencyKey("OUTBOX:ANCHOR_BATCH:" + batch.getPublicId()))
                .willReturn(Optional.of(outbox));
        given(blockchainAnchorPort.getBatch(any())).willReturn(new BlockchainAnchorPort.OnChainBatch(
                com.api.trekkey.domain.credential.crypto.Hash32.of(Hashing.keccak256Utf8("issuer").bytes()),
                com.api.trekkey.domain.credential.crypto.Hash32.of(bytes(2, 32)),
                com.api.trekkey.domain.credential.crypto.Hash32.of(bytes(3, 32)),
                1,
                1,
                1,
                NOW.getEpochSecond(),
                true));

        assertThatThrownBy(() -> service.renewBatchApproval(1L, batch.getPublicId()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getBaseResponseCode().getCode())
                .isEqualTo(CredentialErrorResponseCode.BLOCKCHAIN_RENEWAL_CONFLICT.getCode());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.FAILED);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    void resetsTheFailedWorkOnlyAfterRenewalAndAValidNewSignature() {
        AncBatch batch = failedBatch();
        AncChainTransaction transaction = failedBatchTransaction();
        AncOutboxEvent outbox = deadBatchOutbox();
        given(batchRepository.findByPublicIdForUpdate(batch.getPublicId())).willReturn(Optional.of(batch));
        given(chainTransactionRepository.findByBatchIdAndOperationType(batch.getId(), ChainOperationType.ANCHOR_BATCH))
                .willReturn(Optional.of(transaction));
        given(chainTransactionRepository.findByIdempotencyKeyForUpdate("ANCHOR_BATCH:" + batch.getPublicId()))
                .willReturn(Optional.of(transaction));
        given(outboxEventRepository.findByIdempotencyKey("OUTBOX:ANCHOR_BATCH:" + batch.getPublicId()))
                .willReturn(Optional.of(outbox));
        given(blockchainAnchorPort.getBatch(any())).willReturn(new BlockchainAnchorPort.OnChainBatch(
                com.api.trekkey.domain.credential.crypto.Hash32.of(bytes(1, 32)),
                com.api.trekkey.domain.credential.crypto.Hash32.of(bytes(2, 32)),
                com.api.trekkey.domain.credential.crypto.Hash32.of(bytes(3, 32)),
                1,
                1,
                1,
                0,
                false));
        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeyRepository.findById(5L)).willReturn(Optional.of(issuerKey));
        given(nonceGenerator.next()).willReturn(2L);

        service.renewBatchApproval(1L, batch.getPublicId());

        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.FAILED);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DEAD);
        int attemptsBeforeReset = outbox.getAttemptCount();
        Eip712.BatchApproval approval = ApprovalPayloadFactory.batch(organization.getPublicId(), batch, issuerKey);
        String signature = Eip712.signDigest(
                Eip712.batchDigest(ApprovalPayloadFactory.domain(properties), approval), keyPair).hex();

        service.approveBatch(1L, batch.getPublicId(), signature);

        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.PENDING);
        assertThat(transaction.getTxHash()).isNull();
        assertThat(transaction.getTxNonce()).isNull();
        assertThat(transaction.getSignedRawTransaction()).isNull();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(attemptsBeforeReset);
    }

    @Test
    void reconcilesAConfirmedOnChainBatchWithoutRewritingTheFailedTransactionLedger() {
        AncBatch batch = failedBatch();
        AncChainTransaction transaction = failedBatchTransaction();
        AncOutboxEvent outbox = deadBatchOutbox();
        credential.markBatched();
        AncBatchItem item = AncBatchItem.of(
                batch.getId(), credential.getId(), 0, credential.getCredentialIdHash(), bytes(9, 32), "[]");
        given(batchRepository.findByPublicIdForUpdate(batch.getPublicId())).willReturn(Optional.of(batch));
        given(chainTransactionRepository.findByBatchIdAndOperationType(batch.getId(), ChainOperationType.ANCHOR_BATCH))
                .willReturn(Optional.of(transaction));
        given(outboxEventRepository.findByIdempotencyKey("OUTBOX:ANCHOR_BATCH:" + batch.getPublicId()))
                .willReturn(Optional.of(outbox));
        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeyRepository.findById(5L)).willReturn(Optional.of(issuerKey));
        given(blockchainAnchorPort.getBatch(Hash32.of(batch.getBatchIdHash())))
                .willReturn(new BlockchainAnchorPort.OnChainBatch(
                        Hashing.issuerId(organization.getPublicId()),
                        Hash32.of(batch.getMerkleRoot()),
                        Hash32.of(batch.getSchemaVersionHash()),
                        batch.getLeafCount(),
                        batch.getTreeVersion(),
                        issuerKey.getKeyVersion(),
                        NOW.getEpochSecond(),
                        true));
        given(batchItemRepository.findByBatchIdOrderByLeafIndexAsc(batch.getId())).willReturn(List.of(item));
        given(credentialRepository.findByIdForUpdate(credential.getId())).willReturn(Optional.of(credential));

        SealedBatchView result = service.reconcileBatch(1L, batch.getPublicId());

        assertThat(result.status()).isEqualTo(BatchStatus.ANCHORED);
        assertThat(credential.getStatus()).isEqualTo(CredentialStatus.ANCHORED);
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.FAILED);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    void renewsAndRequeuesAFailedStatusApprovalOnlyAfterTheNewSignatureIsValid() {
        credential.markBatched();
        credential.markAnchored();
        AncCredentialStatusEvent event = failedStatusEvent();
        AncChainTransaction transaction = failedStatusTransaction(event);
        AncOutboxEvent outbox = deadStatusOutbox(event);
        given(statusEventRepository.findByIdForUpdate(event.getId())).willReturn(Optional.of(event));
        given(credentialRepository.findById(credential.getId())).willReturn(Optional.of(credential));
        given(chainTransactionRepository.findByCredentialStatusEventIdAndOperationType(
                event.getId(), ChainOperationType.REVOKE)).willReturn(Optional.of(transaction));
        given(chainTransactionRepository.findByIdempotencyKeyForUpdate("REVOKE:" + credential.getPublicId()))
                .willReturn(Optional.of(transaction));
        given(outboxEventRepository.findByIdempotencyKey("OUTBOX:REVOKE:" + credential.getPublicId()))
                .willReturn(Optional.of(outbox));
        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeyRepository.findById(5L)).willReturn(Optional.of(issuerKey));
        given(blockchainAnchorPort.getCredentialStatus(any(), any()))
                .willReturn(new BlockchainAnchorPort.OnChainCredentialStatus(
                        BlockchainAnchorPort.CredentialChainState.NONE, 0, 0, 0, Hash32.ZERO));
        given(nonceGenerator.next()).willReturn(2L);

        service.renewStatusApproval(1L, event.getId());

        assertThat(event.getApprovalNonce()).isEqualTo(2L);
        assertThat(event.getApprovalDigest()).isNull();
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.FAILED);
        Eip712.StatusApproval approval = ApprovalPayloadFactory.status(
                organization.getPublicId(), event, credential, issuerKey, null);
        String signature = Eip712.signDigest(
                Eip712.statusDigest(ApprovalPayloadFactory.domain(properties), approval), keyPair).hex();

        service.approveStatusChange(1L, event.getId(), signature);

        assertThat(event.getIssuerSignature()).hasSize(65);
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.PENDING);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void reconcilesAnOnChainRevocationWhilePreservingTheFailedDeliveryEvidence() {
        credential.markBatched();
        credential.markAnchored();
        AncCredentialStatusEvent event = failedStatusEvent();
        AncChainTransaction transaction = failedStatusTransaction(event);
        AncOutboxEvent outbox = deadStatusOutbox(event);
        given(statusEventRepository.findByIdForUpdate(event.getId())).willReturn(Optional.of(event));
        given(credentialRepository.findByIdForUpdate(credential.getId())).willReturn(Optional.of(credential));
        given(chainTransactionRepository.findByCredentialStatusEventIdAndOperationType(
                event.getId(), ChainOperationType.REVOKE)).willReturn(Optional.of(transaction));
        given(outboxEventRepository.findByIdempotencyKey("OUTBOX:REVOKE:" + credential.getPublicId()))
                .willReturn(Optional.of(outbox));
        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        given(issuerKeyRepository.findById(5L)).willReturn(Optional.of(issuerKey));
        long effectiveAt = com.api.trekkey.domain.credential.service.support.UtcTime
                .toInstant(event.getEffectiveAt())
                .getEpochSecond();
        given(blockchainAnchorPort.getCredentialStatus(
                Hashing.issuerId(organization.getPublicId()),
                Hash32.of(credential.getCredentialIdHash())))
                .willReturn(new BlockchainAnchorPort.OnChainCredentialStatus(
                        BlockchainAnchorPort.CredentialChainState.REVOKED,
                        effectiveAt,
                        NOW.getEpochSecond(),
                        issuerKey.getKeyVersion(),
                        Hash32.ZERO));

        service.reconcileStatusChange(1L, event.getId());

        assertThat(credential.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(transaction.getStatus()).isEqualTo(ChainTransactionStatus.FAILED);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }

    private AncBatch failedBatch() {
        AncBatch batch = AncBatch.seal(
                1L, 5L, "failed-batch", bytes(1, 32), bytes(2, 32), 1, 1, bytes(3, 32), 1,
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                LocalDateTime.ofInstant(NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));
        ReflectionTestUtils.setField(batch, "id", 22L);
        batch.recordApproval("{\"batch\":1}", bytes(4, 32), bytes(5, 65),
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(50)), ZoneOffset.UTC));
        batch.beginAnchoring();
        batch.markFailed();
        return batch;
    }

    private AncChainTransaction failedBatchTransaction() {
        AncChainTransaction transaction = AncChainTransaction.pending(
                22L, null, null, ChainOperationType.ANCHOR_BATCH, "ANCHOR_BATCH:failed-batch",
                1001L, bytes(6, 20), "v1", null);
        transaction.prepare(new byte[] {1, 2, 3}, bytes(7, 32), 9L, bytes(8, 20),
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(40)), ZoneOffset.UTC));
        transaction.markSubmitted(LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(39)), ZoneOffset.UTC));
        transaction.markFailed("REVERTED");
        ReflectionTestUtils.setField(transaction, "id", 31L);
        return transaction;
    }

    private AncOutboxEvent deadBatchOutbox() {
        AncOutboxEvent outbox = AncOutboxEvent.pending(
                OutboxAggregateType.BATCH, 22L, "ANCHOR_BATCH", "OUTBOX:ANCHOR_BATCH:failed-batch", "{}",
                LocalDateTime.ofInstant(NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));
        outbox.claim("worker-1", LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(40)), ZoneOffset.UTC));
        outbox.markDead("REVERTED");
        ReflectionTestUtils.setField(outbox, "id", 41L);
        return outbox;
    }

    private AncCredentialStatusEvent failedStatusEvent() {
        AncCredentialStatusEvent event = AncCredentialStatusEvent.request(
                credential.getId(),
                issuerKey.getId(),
                CredentialStatus.ANCHORED,
                CredentialStatus.REVOKED,
                "ISSUED_IN_ERROR",
                "학교 확인",
                99L,
                null,
                1,
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                "STATUS:" + credential.getPublicId(),
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(30)), ZoneOffset.UTC));
        event.recordApproval("{\"status\":1}", bytes(10, 32), bytes(11, 65));
        ReflectionTestUtils.setField(event, "id", 51L);
        return event;
    }

    private AncChainTransaction failedStatusTransaction(AncCredentialStatusEvent event) {
        AncChainTransaction transaction = AncChainTransaction.pending(
                null,
                event.getId(),
                null,
                ChainOperationType.REVOKE,
                "REVOKE:" + credential.getPublicId(),
                1001L,
                bytes(12, 20),
                "v1",
                null);
        transaction.prepare(
                new byte[] {1, 2, 3},
                bytes(13, 32),
                10L,
                bytes(14, 20),
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(20)), ZoneOffset.UTC));
        transaction.markSubmitted(LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(19)), ZoneOffset.UTC));
        transaction.markFailed("REVERTED");
        ReflectionTestUtils.setField(transaction, "id", 61L);
        return transaction;
    }

    private AncOutboxEvent deadStatusOutbox(AncCredentialStatusEvent event) {
        AncOutboxEvent outbox = AncOutboxEvent.pending(
                OutboxAggregateType.STATUS_EVENT,
                event.getId(),
                "REVOKE_CREDENTIAL",
                "OUTBOX:REVOKE:" + credential.getPublicId(),
                "{}",
                LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(30)), ZoneOffset.UTC));
        outbox.claim("worker-1", LocalDateTime.ofInstant(NOW.minus(Duration.ofMinutes(20)), ZoneOffset.UTC));
        outbox.markDead("REVERTED");
        ReflectionTestUtils.setField(outbox, "id", 71L);
        return outbox;
    }

    private Organization organization() {
        Organization value = org.springframework.beans.BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(value, "id", 1L);
        ReflectionTestUtils.setField(value, "publicId", "organization-public-1");
        ReflectionTestUtils.setField(value, "name", "트레키대학교");
        return value;
    }

    private AncIssuerKey issuerKey() {
        EthereumAddress signer = EthereumAddress.fromPublicKey(keyPair.getPublicKey());
        AncIssuerKey value = AncIssuerKey.activate(
                1L,
                1,
                signer.bytes(),
                "external-signer:1",
                LocalDateTime.ofInstant(NOW.minus(Duration.ofDays(1)), ZoneOffset.UTC),
                null);
        ReflectionTestUtils.setField(value, "id", 5L);
        return value;
    }

    private AncCredential credential() {
        AncCredential value = AncCredential.ready(
                1L,
                "credential-public-1",
                Hashing.keccak256Utf8("credential-1").bytes(),
                "AWARD-1",
                CredentialType.AWARD,
                CredentialSchemaProfiles.AWARD_V1,
                Hashing.schemaVersion(CredentialSchemaProfiles.AWARD_V1).bytes(),
                "{}",
                new byte[] {1},
                new byte[] {2},
                Hashing.sha256(new byte[] {1}).bytes(),
                Hashing.sha256(new byte[] {2}).bytes(),
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC),
                null);
        ReflectionTestUtils.setField(value, "id", 10L);
        return value;
    }

    private byte[] bytes(int firstByte, int length) {
        byte[] value = new byte[length];
        value[0] = (byte) firstByte;
        return value;
    }
}
