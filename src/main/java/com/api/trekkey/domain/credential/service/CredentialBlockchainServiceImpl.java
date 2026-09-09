package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.CredentialLeaf;
import com.api.trekkey.domain.credential.crypto.CryptoValidationException;
import com.api.trekkey.domain.credential.crypto.Eip712;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.crypto.Signature65;
import com.api.trekkey.domain.credential.crypto.StandardMerkleTree;
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
import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import com.api.trekkey.domain.credential.entity.OutboxAggregateType;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialStatusEventRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.repository.AncOutboxEventRepository;
import com.api.trekkey.domain.credential.service.dto.BlockchainApprovalView;
import com.api.trekkey.domain.credential.service.dto.CredentialStatusEventView;
import com.api.trekkey.domain.credential.service.dto.IssuerKeyView;
import com.api.trekkey.domain.credential.service.dto.SealedBatchView;
import com.api.trekkey.domain.credential.service.dto.StatusChangeCommand;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import com.api.trekkey.domain.credential.service.support.ApprovalNonceGenerator;
import com.api.trekkey.domain.credential.service.support.ApprovalPayloadFactory;
import com.api.trekkey.domain.credential.service.support.UtcTime;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CredentialBlockchainServiceImpl implements CredentialBlockchainService {

    private static final String EVENT_ANCHOR_BATCH = "ANCHOR_BATCH";
    private static final String EVENT_REVOKE = "REVOKE_CREDENTIAL";
    private static final String EVENT_SUPERSEDE = "SUPERSEDE_CREDENTIAL";

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AncIssuerKeyRepository issuerKeyRepository;
    private final AncCredentialRepository credentialRepository;
    private final AncBatchRepository batchRepository;
    private final AncBatchItemRepository batchItemRepository;
    private final AncCredentialStatusEventRepository statusEventRepository;
    private final AncChainTransactionRepository chainTransactionRepository;
    private final AncOutboxEventRepository outboxEventRepository;
    private final BlockchainAnchorPort blockchainAnchorPort;
    private final BlockchainProperties properties;
    private final ApprovalNonceGenerator nonceGenerator;
    private final ObjectMapper objectMapper;
    private final Clock credentialClock;

    @Override
    public IssuerKeyView syncIssuerKey(Long organizationId, int keyVersion, String signerRef) {
        if (keyVersion < 1 || signerRef == null || signerRef.isBlank()) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
        }
        Organization organization = organizationForUpdate(organizationId);
        Hash32 issuerId = Hashing.issuerId(organization.ensurePublicId());
        BlockchainAnchorPort.OnChainIssuerKey onChainKey = chainRead(
                () -> blockchainAnchorPort.getIssuerKey(issuerId, keyVersion));
        if (!onChainKey.exists()) {
            throw new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND);
        }

        AncIssuerKey issuerKey = issuerKeyRepository.findByOrganizationIdAndKeyVersion(organizationId, keyVersion)
                .map(existing -> synchronizeExistingKey(existing, onChainKey))
                .orElseGet(() -> synchronizeNewKey(
                        organizationId,
                        keyVersion,
                        signerRef,
                        onChainKey));
        return issuerKeyView(issuerKey);
    }

    @Override
    public SealedBatchView sealBatch(Long organizationId, String schemaProfileId, int keyVersion) {
        requireReadConfiguration();
        if (!supportsProfile(schemaProfileId) || keyVersion < 1) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
        }
        Organization organization = organization(organizationId);
        AncIssuerKey issuerKey = activeIssuerKey(organizationId, keyVersion);
        Hash32 schemaVersionHash = Hashing.schemaVersion(schemaProfileId);
        List<AncCredential> credentials = credentialRepository.findBatchClaimCandidatesForUpdate(
                organizationId,
                schemaVersionHash.bytes(),
                CredentialStatus.READY,
                PageRequest.of(0, properties.getBatchSize()));
        if (credentials.isEmpty()) {
            throw new CustomException(CredentialErrorResponseCode.NO_READY_CREDENTIAL);
        }

        Hash32 issuerId = Hashing.issuerId(organization.ensurePublicId());
        List<LeafItem> leaves = credentials.stream()
                .map(credential -> leafItem(issuerId, credential))
                .toList();
        StandardMerkleTree tree = StandardMerkleTree.fromLeafHashes(
                leaves.stream().map(LeafItem::leafHash).toList());
        Instant now = credentialClock.instant();
        Instant deadline = now.plus(properties.getApprovalTtl());
        String publicId = UUID.randomUUID().toString();
        Hash32 batchIdHash = Hashing.batchId(issuerId, publicId);

        AncBatch batch = batchRepository.save(AncBatch.seal(
                organizationId,
                issuerKey.getId(),
                publicId,
                batchIdHash.bytes(),
                schemaVersionHash.bytes(),
                properties.getTreeVersion(),
                credentials.size(),
                tree.root().bytes(),
                nonceGenerator.next(),
                UtcTime.toLocalDateTime(deadline),
                UtcTime.toLocalDateTime(now)).inContext(properties.chainContext()));

        batchItemRepository.saveAll(leaves.stream()
                .map(item -> batchItem(batch, tree, item))
                .toList());
        credentials.forEach(AncCredential::markBatched);
        return batchView(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SealedBatchView> getBatches(Long organizationId) {
        return batchRepository
                .findAllByIssuerOrganizationIdOrderBySealedAtDescIdDesc(
                        organizationId)
                .stream()
                .map(this::batchView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BlockchainApprovalView getBatchApproval(Long organizationId, String batchPublicId) {
        AncBatch batch = batch(organizationId, batchPublicId);
        return batchApprovalView(batch);
    }

    @Override
    public BlockchainApprovalView renewBatchApproval(Long organizationId, String batchPublicId) {
        requireWriteConfiguration();
        AncBatch batch = batchRepository.findByPublicIdForUpdate(batchPublicId)
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.BATCH_NOT_FOUND));
        requireContext(batch.getChainContext());
        Instant now = credentialClock.instant();
        requireApprovalRenewalDue(batch.getApprovalDeadline(), now);
        boolean unsigned = batch.getStatus() == BatchStatus.SEALED && batch.getApprovalDigest() == null;
        if (!unsigned) {
            requireFailedBatchWork(batch);
            BlockchainAnchorPort.OnChainBatch onChainBatch = chainRead(
                    () -> blockchainAnchorPort.getBatch(Hash32.of(batch.getBatchIdHash())));
            if (onChainBatch.exists()) {
                throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_RENEWAL_CONFLICT);
            }
        }
        batch.renewApproval(nextApprovalNonce(batch.getApprovalNonce()), approvalDeadline(now));
        return batchApprovalView(batch);
    }

    @Override
    public SealedBatchView reconcileBatch(Long organizationId, String batchPublicId) {
        requireReadConfiguration();
        AncBatch batch = batchRepository.findByPublicIdForUpdate(batchPublicId)
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.BATCH_NOT_FOUND));
        requireContext(batch.getChainContext());
        if (batch.getStatus() == BatchStatus.ANCHORED) {
            return batchView(batch);
        }
        requireFailedBatchWork(batch);

        Organization organization = organization(organizationId);
        AncIssuerKey issuerKey = issuerKeyRepository.findById(batch.getIssuerKeyId())
                .filter(candidate -> candidate.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));
        Hash32 issuerId = Hashing.issuerId(existingOrganizationPublicId(organization));
        BlockchainAnchorPort.OnChainBatch actual = chainRead(
                () -> blockchainAnchorPort.getBatch(Hash32.of(batch.getBatchIdHash())));
        if (!batchReconciliationMatches(batch, issuerKey, issuerId, actual)) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_RECONCILIATION_MISMATCH);
        }

        List<AncBatchItem> items = batchItemRepository.findByBatchIdOrderByLeafIndexAsc(batch.getId());
        if (items.size() != batch.getLeafCount()) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_RECONCILIATION_MISMATCH);
        }
        for (AncBatchItem item : items) {
            AncCredential credential = credentialRepository.findByIdForUpdate(item.getCredentialId())
                    .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
            if (credential.getStatus() == CredentialStatus.BATCHED) {
                credential.markAnchored();
            } else if (credential.getStatus() != CredentialStatus.ANCHORED) {
                throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_RECONCILIATION_MISMATCH);
            }
        }
        batch.reconcileAnchored();
        return batchView(batch);
    }

    @Override
    public SealedBatchView approveBatch(Long organizationId, String batchPublicId, String signatureHex) {
        requireWriteConfiguration();
        AncBatch batch = batchRepository.findByPublicIdForUpdate(batchPublicId)
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.BATCH_NOT_FOUND));
        requireContext(batch.getChainContext());
        Signature65 signature = signature(signatureHex);
        if (batch.getStatus() != BatchStatus.SEALED) {
            if (batch.getIssuerSignature() != null && Arrays.equals(batch.getIssuerSignature(), signature.bytes())) {
                return batchView(batch);
            }
            throw new CustomException(CredentialErrorResponseCode.INVALID_BATCH_STATE);
        }

        Instant now = credentialClock.instant();
        if (!now.isBefore(UtcTime.toInstant(batch.getApprovalDeadline()))) {
            throw new CustomException(CredentialErrorResponseCode.APPROVAL_EXPIRED);
        }
        AncIssuerKey issuerKey = activeIssuerKey(batch.getIssuerOrganizationId(), batch.getIssuerKeyId());
        Eip712.BatchApproval approval = batchApproval(batch);
        Hash32 digest = ApprovalPayloadFactory.batchDigest(properties, approval);
        verifySigner(digest, signature, issuerKey);
        batch.recordApproval(
                ApprovalPayloadFactory.batchPayload(properties, approval),
                digest.bytes(),
                signature.bytes(),
                UtcTime.toLocalDateTime(now));

        createChainWork(
                batch.getId(),
                null,
                ChainOperationType.ANCHOR_BATCH,
                OutboxAggregateType.BATCH,
                EVENT_ANCHOR_BATCH,
                "ANCHOR_BATCH:" + batch.getPublicId(),
                now);
        return batchView(batch);
    }

    @Override
    public BlockchainApprovalView requestStatusChange(
            Long organizationId,
            Long actorUserId,
            String credentialPublicId,
            StatusChangeCommand command) {
        requireReadConfiguration();
        validateStatusCommand(command);
        verifyActorOrganization(actorUserId, organizationId);
        AncCredential credential = credentialRepository.findByPublicIdForUpdate(credentialPublicId)
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        if (credential.getStatus() != CredentialStatus.ANCHORED
                || statusEventRepository.existsByCredentialId(credential.getId())) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE);
        }

        requireCredentialContext(credential);

        AncIssuerKey issuerKey = activeIssuerKey(organizationId, command.issuerKeyVersion());
        AncCredential replacement = replacementCredential(organizationId, credential, command);
        Instant effectiveAt = credentialClock.instant();
        Instant deadline = effectiveAt.plus(properties.getApprovalTtl());
        CredentialStatus nextStatus = command.action() == StatusChangeCommand.Action.REVOKE
                ? CredentialStatus.REVOKED
                : CredentialStatus.SUPERSEDED;
        AncCredentialStatusEvent event = statusEventRepository.save(AncCredentialStatusEvent.request(
                credential.getId(),
                issuerKey.getId(),
                credential.getStatus(),
                nextStatus,
                command.reasonCode(),
                command.reasonDetail(),
                actorUserId,
                replacement == null ? null : replacement.getId(),
                nonceGenerator.next(),
                UtcTime.toLocalDateTime(deadline),
                "STATUS:" + credential.getPublicId(),
                UtcTime.toLocalDateTime(effectiveAt)).inContext(properties.chainContext()));
        return statusApprovalView(event, credential, issuerKey, replacement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CredentialStatusEventView> getStatusEvents(
            Long organizationId) {
        return statusEventRepository.findAllRowsByOrganizationId(organizationId)
                .stream()
                .map(this::statusEventView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BlockchainApprovalView getStatusApproval(Long organizationId, Long statusEventId) {
        AncCredentialStatusEvent event = statusEventRepository.findById(statusEventId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        AncCredential credential = credentialRepository.findById(event.getCredentialId())
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        AncIssuerKey issuerKey = issuerKeyRepository.findById(event.getIssuerKeyId())
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));
        AncCredential replacement = event.getSupersedingCredentialId() == null
                ? null
                : credentialRepository.findById(event.getSupersedingCredentialId()).orElseThrow(
                        () -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        return statusApprovalView(event, credential, issuerKey, replacement);
    }

    @Override
    public BlockchainApprovalView renewStatusApproval(Long organizationId, Long statusEventId) {
        requireWriteConfiguration();
        AncCredentialStatusEvent event = statusEventRepository.findByIdForUpdate(statusEventId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        AncCredential credential = credentialRepository.findById(event.getCredentialId())
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        requireContext(event.getChainContext());
        Instant now = credentialClock.instant();
        requireApprovalRenewalDue(event.getApprovalDeadline(), now);
        if (event.getApprovalDigest() != null) {
            requireFailedStatusWork(event, credential.getPublicId());
            Organization organization = organization(organizationId);
            BlockchainAnchorPort.OnChainCredentialStatus onChainStatus = chainRead(
                    () -> blockchainAnchorPort.getCredentialStatus(
                            Hashing.issuerId(existingOrganizationPublicId(organization)),
                            Hash32.of(credential.getCredentialIdHash())));
            if (onChainStatus.state() != BlockchainAnchorPort.CredentialChainState.NONE) {
                throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_RENEWAL_CONFLICT);
            }
        }
        event.renewApproval(
                nextApprovalNonce(event.getApprovalNonce()),
                approvalDeadline(now),
                UtcTime.toLocalDateTime(now));
        AncIssuerKey issuerKey = issuerKeyRepository.findById(event.getIssuerKeyId())
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));
        AncCredential replacement = event.getSupersedingCredentialId() == null
                ? null
                : credentialRepository.findById(event.getSupersedingCredentialId()).orElseThrow(
                        () -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        return statusApprovalView(event, credential, issuerKey, replacement);
    }

    @Override
    public void reconcileStatusChange(Long organizationId, Long statusEventId) {
        requireReadConfiguration();
        AncCredentialStatusEvent event = statusEventRepository.findByIdForUpdate(statusEventId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        AncCredential credential = credentialRepository.findByIdForUpdate(event.getCredentialId())
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        requireContext(event.getChainContext());
        if (credential.getStatus() == event.getNextStatus()) {
            return;
        }
        if (credential.getStatus() != CredentialStatus.ANCHORED) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE);
        }
        requireFailedStatusWork(event, credential.getPublicId());

        Organization organization = organization(organizationId);
        Hash32 issuerId = Hashing.issuerId(existingOrganizationPublicId(organization));
        AncIssuerKey issuerKey = issuerKeyRepository.findById(event.getIssuerKeyId())
                .filter(candidate -> candidate.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));
        AncCredential replacement = event.getSupersedingCredentialId() == null
                ? null
                : credentialRepository.findById(event.getSupersedingCredentialId())
                        .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                        .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        BlockchainAnchorPort.OnChainCredentialStatus actual = chainRead(
                () -> blockchainAnchorPort.getCredentialStatus(
                        issuerId,
                        Hash32.of(credential.getCredentialIdHash())));
        if (!statusReconciliationMatches(event, issuerKey, replacement, actual)) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_RECONCILIATION_MISMATCH);
        }

        if (event.getNextStatus() == CredentialStatus.REVOKED) {
            credential.markRevoked();
        } else {
            credential.markSuperseded();
        }
    }

    @Override
    public void approveStatusChange(Long organizationId, Long statusEventId, String signatureHex) {
        requireWriteConfiguration();
        AncCredentialStatusEvent event = statusEventRepository.findByIdForUpdate(statusEventId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        AncCredential credential = credentialRepository.findById(event.getCredentialId())
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.STATUS_EVENT_NOT_FOUND));
        requireContext(event.getChainContext());
        Signature65 signature = signature(signatureHex);
        if (event.getIssuerSignature() != null) {
            if (Arrays.equals(event.getIssuerSignature(), signature.bytes())) {
                return;
            }
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE);
        }
        Instant now = credentialClock.instant();
        if (!now.isBefore(UtcTime.toInstant(event.getApprovalDeadline()))) {
            throw new CustomException(CredentialErrorResponseCode.APPROVAL_EXPIRED);
        }
        AncIssuerKey issuerKey = activeIssuerKey(organizationId, event.getIssuerKeyId());
        AncCredential replacement = event.getSupersedingCredentialId() == null
                ? null
                : credentialRepository.findById(event.getSupersedingCredentialId()).orElseThrow(
                        () -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        Eip712.StatusApproval approval = statusApproval(event, credential, issuerKey, replacement);
        requireContext(event.getChainContext());
        Hash32 digest = ApprovalPayloadFactory.statusDigest(properties, approval);
        verifySigner(digest, signature, issuerKey);
        event.recordApproval(ApprovalPayloadFactory.statusPayload(properties, approval), digest.bytes(), signature.bytes());

        ChainOperationType operationType = event.getNextStatus() == CredentialStatus.REVOKED
                ? ChainOperationType.REVOKE
                : ChainOperationType.SUPERSEDE;
        String eventType = operationType == ChainOperationType.REVOKE ? EVENT_REVOKE : EVENT_SUPERSEDE;
        createChainWork(
                null,
                event.getId(),
                operationType,
                OutboxAggregateType.STATUS_EVENT,
                eventType,
                operationType.name() + ":" + credential.getPublicId(),
                now);
    }

    private Organization organization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new CustomException(OrganizationErrorResponseCode.ORGANIZATION_NOT_FOUND));
    }

    private Organization organizationForUpdate(Long organizationId) {
        return organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new CustomException(OrganizationErrorResponseCode.ORGANIZATION_NOT_FOUND));
    }

    private AncIssuerKey activeIssuerKey(Long organizationId, int keyVersion) {
        AncIssuerKey issuerKey = issuerKeyRepository.findByOrganizationIdAndKeyVersion(organizationId, keyVersion)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));
        return requireActive(issuerKey);
    }

    private AncIssuerKey activeIssuerKey(Long organizationId, Long issuerKeyId) {
        AncIssuerKey issuerKey = issuerKeyRepository.findById(issuerKeyId)
                .filter(candidate -> candidate.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));
        return requireActive(issuerKey);
    }

    private AncIssuerKey requireActive(AncIssuerKey issuerKey) {
        requireContext(issuerKey.getChainContext());
        LocalDateTime now = UtcTime.toLocalDateTime(credentialClock.instant());
        if (issuerKey.getStatus() != IssuerKeyStatus.ACTIVE
                || now.isBefore(issuerKey.getValidFrom())
                || (issuerKey.getValidUntil() != null && now.isAfter(issuerKey.getValidUntil()))
                || (issuerKey.getCompromisedAt() != null && !now.isBefore(issuerKey.getCompromisedAt()))) {
            throw new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND);
        }
        return issuerKey;
    }

    private AncIssuerKey synchronizeExistingKey(
            AncIssuerKey existing,
            BlockchainAnchorPort.OnChainIssuerKey onChainKey) {
        requireContext(existing.getChainContext());
        LocalDateTime validFrom = epochSecond(onChainKey.validFrom());
        if (!Arrays.equals(existing.getSignerAddress(), onChainKey.signer().bytes())
                || !existing.getValidFrom().equals(validFrom)) {
            throw new CustomException(CredentialErrorResponseCode.ISSUER_KEY_MISMATCH);
        }
        try {
            existing.synchronizeLifecycle(
                    optionalEpochSecond(onChainKey.validUntil()),
                    optionalEpochSecond(onChainKey.compromisedAt()));
            return existing;
        } catch (IllegalStateException exception) {
            throw new CustomException(CredentialErrorResponseCode.ISSUER_KEY_MISMATCH);
        }
    }

    private AncIssuerKey synchronizeNewKey(
            Long organizationId,
            int keyVersion,
            String signerRef,
            BlockchainAnchorPort.OnChainIssuerKey onChainKey) {
        AncIssuerKey issuerKey = AncIssuerKey.activate(
                organizationId,
                keyVersion,
                onChainKey.signer().bytes(),
                signerRef,
                epochSecond(onChainKey.validFrom()),
                null).inContext(properties.chainContext());
        try {
            issuerKey.synchronizeLifecycle(
                    optionalEpochSecond(onChainKey.validUntil()),
                    optionalEpochSecond(onChainKey.compromisedAt()));
            return issuerKeyRepository.save(issuerKey);
        } catch (IllegalStateException exception) {
            throw new CustomException(CredentialErrorResponseCode.ISSUER_KEY_MISMATCH);
        }
    }

    private LeafItem leafItem(Hash32 issuerId, AncCredential credential) {
        CredentialLeaf leaf = new CredentialLeaf(
                issuerId,
                Hash32.of(credential.getCredentialIdHash()),
                Hash32.of(credential.getSchemaVersionHash()),
                Hash32.of(credential.getContentHash()),
                Hash32.of(credential.getFileManifestHash()));
        return new LeafItem(credential, leaf.hash());
    }

    private AncBatchItem batchItem(AncBatch batch, StandardMerkleTree tree, LeafItem item) {
        return AncBatchItem.of(
                batch.getId(),
                item.credential().getId(),
                tree.leafIndex(item.leafHash()),
                item.credential().getCredentialIdHash(),
                item.leafHash().bytes(),
                json(tree.proofForLeaf(item.leafHash()).stream().map(Hash32::hex).toList()));
    }

    private Eip712.BatchApproval batchApproval(AncBatch batch) {
        requireContext(batch.getChainContext());
        Organization organization = organization(batch.getIssuerOrganizationId());
        AncIssuerKey issuerKey = issuerKeyRepository.findById(batch.getIssuerKeyId())
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.ISSUER_KEY_NOT_FOUND));
        return ApprovalPayloadFactory.batch(existingOrganizationPublicId(organization), batch, issuerKey);
    }

    private BlockchainApprovalView batchApprovalView(AncBatch batch) {
        Eip712.BatchApproval approval = batchApproval(batch);
        requireReadConfiguration();
        return new BlockchainApprovalView(
                "BATCH",
                batch.getPublicId(),
                ApprovalPayloadFactory.batchPayload(properties, approval),
                ApprovalPayloadFactory.batchDigest(properties, approval).hex(),
                batch.getApprovalNonce(),
                UtcTime.toInstant(batch.getApprovalDeadline()));
    }

    private Eip712.StatusApproval statusApproval(
            AncCredentialStatusEvent event,
            AncCredential credential,
            AncIssuerKey issuerKey,
            AncCredential replacement) {
        requireContext(event.getChainContext());
        Organization organization = organization(credential.getIssuerOrganizationId());
        return ApprovalPayloadFactory.status(
                existingOrganizationPublicId(organization),
                event,
                credential,
                issuerKey,
                replacement);
    }

    private BlockchainApprovalView statusApprovalView(
            AncCredentialStatusEvent event,
            AncCredential credential,
            AncIssuerKey issuerKey,
            AncCredential replacement) {
        Eip712.StatusApproval approval = statusApproval(event, credential, issuerKey, replacement);
        requireReadConfiguration();
        return new BlockchainApprovalView(
                "STATUS_EVENT",
                String.valueOf(event.getId()),
                ApprovalPayloadFactory.statusPayload(properties, approval),
                ApprovalPayloadFactory.statusDigest(properties, approval).hex(),
                event.getApprovalNonce(),
                UtcTime.toInstant(event.getApprovalDeadline()));
    }

    private AncCredential replacementCredential(
            Long organizationId,
            AncCredential original,
            StatusChangeCommand command) {
        if (command.action() == StatusChangeCommand.Action.REVOKE) {
            if (command.replacementCredentialPublicId() != null) {
                throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
            }
            return null;
        }
        if (command.replacementCredentialPublicId() == null) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
        }
        AncCredential replacement = credentialRepository.findByPublicId(command.replacementCredentialPublicId())
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        if (replacement.getId().equals(original.getId()) || replacement.getStatus() != CredentialStatus.ANCHORED) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE);
        }
        requireCredentialContext(replacement);
        return replacement;
    }

    private void createChainWork(
            Long batchId,
            Long statusEventId,
            ChainOperationType operationType,
            OutboxAggregateType aggregateType,
            String eventType,
            String idempotencyKey,
            Instant now) {
        Optional<AncChainTransaction> existingTransaction = chainTransactionRepository
                .findByIdempotencyKeyForUpdate(idempotencyKey);
        if (existingTransaction.isPresent()) {
            AncChainTransaction transaction = existingTransaction.get();
            requireContext(transaction.getChainContext());
            AncOutboxEvent outbox = outboxEventRepository.findByIdempotencyKey("OUTBOX:" + idempotencyKey)
                    .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE));
            if (transaction.getStatus() == ChainTransactionStatus.FAILED
                    && outbox.getStatus() == OutboxStatus.DEAD) {
                transaction.resetForApprovalRenewal();
                outbox.resetForApprovalRenewal(UtcTime.toLocalDateTime(now));
            }
            return;
        }
        AncChainTransaction transaction = chainTransactionRepository.save(AncChainTransaction.pending(
                batchId,
                statusEventId,
                null,
                operationType,
                idempotencyKey,
                properties.ledgerChainId(),
                properties.ledgerContractAddress(),
                properties.getContractVersion(),
                UtcTime.toLocalDateTime(now)).inContext(properties.chainContext()));
        Long aggregateId = batchId == null ? statusEventId : batchId;
        outboxEventRepository.save(AncOutboxEvent.pending(
                aggregateType,
                aggregateId,
                eventType,
                "OUTBOX:" + idempotencyKey,
                json(new ChainWorkPayload(transaction.getId(), operationType.name())),
                UtcTime.toLocalDateTime(now)));
    }

    private void requireApprovalRenewalDue(LocalDateTime deadline, Instant now) {
        if (now.isBefore(UtcTime.toInstant(deadline))) {
            throw new CustomException(CredentialErrorResponseCode.APPROVAL_RENEWAL_NOT_DUE);
        }
    }

    private LocalDateTime approvalDeadline(Instant now) {
        return UtcTime.toLocalDateTime(now.plus(properties.getApprovalTtl()));
    }

    private long nextApprovalNonce(long previousNonce) {
        for (int attempt = 0; attempt < 8; attempt++) {
            long nonce = nonceGenerator.next();
            if (nonce != 0 && nonce != previousNonce) {
                return nonce;
            }
        }
        throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
    }

    private void requireFailedBatchWork(AncBatch batch) {
        AncChainTransaction transaction = chainTransactionRepository
                .findByBatchIdAndOperationType(batch.getId(), ChainOperationType.ANCHOR_BATCH)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.INVALID_BATCH_STATE));
        AncOutboxEvent outbox = outboxEventRepository.findByIdempotencyKey(
                        "OUTBOX:ANCHOR_BATCH:" + batch.getPublicId())
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.INVALID_BATCH_STATE));
        if (transaction.getStatus() != ChainTransactionStatus.FAILED || outbox.getStatus() != OutboxStatus.DEAD) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_BATCH_STATE);
        }
    }

    private void requireFailedStatusWork(AncCredentialStatusEvent event, String credentialPublicId) {
        ChainOperationType operationType = event.getNextStatus() == CredentialStatus.REVOKED
                ? ChainOperationType.REVOKE
                : ChainOperationType.SUPERSEDE;
        AncChainTransaction transaction = chainTransactionRepository
                .findByCredentialStatusEventIdAndOperationType(event.getId(), operationType)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE));
        AncOutboxEvent outbox = outboxEventRepository.findByIdempotencyKey(
                        "OUTBOX:" + operationType.name() + ":" + credentialPublicId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE));
        if (transaction.getStatus() != ChainTransactionStatus.FAILED || outbox.getStatus() != OutboxStatus.DEAD) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_STATE);
        }
    }

    private boolean batchReconciliationMatches(
            AncBatch batch,
            AncIssuerKey issuerKey,
            Hash32 issuerId,
            BlockchainAnchorPort.OnChainBatch actual) {
        return actual.exists()
                && actual.issuerId().equals(issuerId)
                && actual.merkleRoot().equals(Hash32.of(batch.getMerkleRoot()))
                && actual.schemaVersionHash().equals(Hash32.of(batch.getSchemaVersionHash()))
                && actual.leafCount() == batch.getLeafCount()
                && actual.treeVersion() == batch.getTreeVersion()
                && actual.issuerKeyVersion() == issuerKey.getKeyVersion()
                && actual.anchoredAt() > 0;
    }

    private boolean statusReconciliationMatches(
            AncCredentialStatusEvent event,
            AncIssuerKey issuerKey,
            AncCredential replacement,
            BlockchainAnchorPort.OnChainCredentialStatus actual) {
        BlockchainAnchorPort.CredentialChainState expectedState =
                event.getNextStatus() == CredentialStatus.REVOKED
                        ? BlockchainAnchorPort.CredentialChainState.REVOKED
                        : BlockchainAnchorPort.CredentialChainState.SUPERSEDED;
        Hash32 expectedReplacement = replacement == null
                ? Hash32.ZERO
                : Hash32.of(replacement.getCredentialIdHash());
        long effectiveAt = UtcTime.toInstant(event.getEffectiveAt()).getEpochSecond();
        return actual.state() == expectedState
                && actual.effectiveAt() == effectiveAt
                && actual.recordedAt() >= effectiveAt
                && actual.issuerKeyVersion() == issuerKey.getKeyVersion()
                && actual.replacementCredentialIdHash().equals(expectedReplacement);
    }

    private void verifyActorOrganization(Long actorUserId, Long organizationId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT));
        if (!actor.getOrganization().getId().equals(organizationId)) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
        }
    }

    private void verifySigner(Hash32 digest, Signature65 signature, AncIssuerKey issuerKey) {
        if (!Eip712.recoverSigner(digest, signature)
                .equals(EthereumAddress.fromHex(toHex(issuerKey.getSignerAddress())))) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_ISSUER_SIGNATURE);
        }
    }

    private void validateStatusCommand(StatusChangeCommand command) {
        if (command == null
                || command.action() == null
                || command.issuerKeyVersion() < 1
                || command.reasonCode() == null
                || command.reasonCode().isBlank()) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
        }
    }

    private boolean supportsProfile(String schemaProfileId) {
        return Arrays.stream(CredentialType.values())
                .anyMatch(type -> CredentialSchemaProfiles.supports(type, schemaProfileId));
    }

    private SealedBatchView batchView(AncBatch batch) {
        return new SealedBatchView(
                batch.getPublicId(),
                batch.getStatus(),
                batch.getLeafCount(),
                Hash32.of(batch.getMerkleRoot()).hex(),
                UtcTime.toInstant(batch.getApprovalDeadline()));
    }

    private CredentialStatusEventView statusEventView(
            AncCredentialStatusEventRepository.StatusEventRow row) {
        return new CredentialStatusEventView(
                row.getId(),
                row.getCredentialPublicId(),
                row.getCredentialNo(),
                CredentialStatus.valueOf(row.getCredentialStatus()),
                CredentialStatus.valueOf(row.getPreviousStatus()),
                CredentialStatus.valueOf(row.getNextStatus()),
                row.getReasonCode(),
                row.getReasonDetail(),
                row.getActorUserId(),
                row.getSupersedingCredentialPublicId(),
                row.getIssuerSignature() != null,
                UtcTime.toInstant(row.getApprovalDeadline()),
                UtcTime.toInstant(row.getEffectiveAt()),
                row.getTransactionStatus() == null
                        ? null
                        : ChainTransactionStatus.valueOf(
                                row.getTransactionStatus()),
                row.getLastErrorCode(),
                UtcTime.toInstant(row.getCreatedAt()));
    }

    private IssuerKeyView issuerKeyView(AncIssuerKey issuerKey) {
        return new IssuerKeyView(
                issuerKey.getKeyVersion(),
                toHex(issuerKey.getSignerAddress()),
                issuerKey.getStatus(),
                UtcTime.toInstant(issuerKey.getValidFrom()),
                issuerKey.getValidUntil() == null ? null : UtcTime.toInstant(issuerKey.getValidUntil()),
                issuerKey.getCompromisedAt() == null ? null : UtcTime.toInstant(issuerKey.getCompromisedAt()));
    }

    private AncBatch batch(Long organizationId, String batchPublicId) {
        return batchRepository.findByPublicId(batchPublicId)
                .filter(candidate -> candidate.getIssuerOrganizationId().equals(organizationId))
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.BATCH_NOT_FOUND));
    }

    private Eip712.Domain domain() {
        requireReadConfiguration();
        try {
            return ApprovalPayloadFactory.domain(properties);
        } catch (CryptoValidationException exception) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_CONFIGURATION_INVALID);
        }
    }

    private EthereumAddress contractAddress() {
        try {
            return EthereumAddress.fromHex(properties.getContractAddress());
        } catch (CryptoValidationException exception) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_CONFIGURATION_INVALID);
        }
    }

    private Signature65 signature(String signatureHex) {
        try {
            return Signature65.fromHex(signatureHex);
        } catch (CryptoValidationException exception) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_ISSUER_SIGNATURE);
        }
    }

    private void requireReadConfiguration() {
        if (!properties.isReadEnabled()) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_READ_DISABLED);
        }
        if ((!properties.isSui() && (properties.getChainId() <= 0 || properties.getContractAddress() == null))
                || (properties.isSui() && (properties.getSui().getPackageId() == null
                        || properties.getSui().getRegistryId() == null || properties.getSui().getChainIdentifier() == null))
                || properties.getApprovalTtl().isZero()
                || properties.getApprovalTtl().isNegative()
                || properties.getBatchSize() < 1
                || properties.getTreeVersion() < 1) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_CONFIGURATION_INVALID);
        }
    }

    private void requireContext(String context) {
        if (!properties.matchesContext(context)) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_CONFIGURATION_INVALID);
        }
    }

    private void requireCredentialContext(AncCredential credential) {
        // Legacy mocks/rows have no context; only legacy mode may accept them.
        var item = batchItemRepository.findByCredentialId(credential.getId());
        if (item.isEmpty()) {
            if (properties.isSui()) throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_CONFIGURATION_INVALID);
            return;
        }
        AncBatch batch = batchRepository.findById(item.get().getBatchId())
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.BATCH_NOT_FOUND));
        requireContext(batch.getChainContext());
    }

    private void requireWriteConfiguration() {
        requireReadConfiguration();
        if (!properties.isWriteEnabled()) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_WRITE_DISABLED);
        }
    }

    private <T> T chainRead(ChainSupplier<T> supplier) {
        requireReadConfiguration();
        try {
            return supplier.get();
        } catch (BlockchainGatewayException exception) {
            throw new CustomException(exception.isRetryable()
                    ? CredentialErrorResponseCode.BLOCKCHAIN_RPC_UNAVAILABLE
                    : CredentialErrorResponseCode.BLOCKCHAIN_TRANSACTION_FAILED);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize blockchain work payload", exception);
        }
    }

    private LocalDateTime epochSecond(long value) {
        if (value <= 0) {
            throw new CustomException(CredentialErrorResponseCode.ISSUER_KEY_MISMATCH);
        }
        return UtcTime.toLocalDateTime(Instant.ofEpochSecond(value));
    }

    private LocalDateTime optionalEpochSecond(long value) {
        return value == 0 ? null : epochSecond(value);
    }

    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(2 + value.length * 2);
        builder.append("0x");
        for (byte current : value) {
            builder.append(Character.forDigit((current >>> 4) & 0x0f, 16));
            builder.append(Character.forDigit(current & 0x0f, 16));
        }
        return builder.toString();
    }

    private String existingOrganizationPublicId(Organization organization) {
        String publicId = organization.getPublicId();
        if (publicId == null || publicId.isBlank()) {
            throw new CustomException(CredentialErrorResponseCode.BLOCKCHAIN_CONFIGURATION_INVALID);
        }
        return publicId;
    }

    private record LeafItem(AncCredential credential, Hash32 leafHash) {
    }

    private record ChainWorkPayload(Long chainTransactionId, String operationType) {
    }

    @FunctionalInterface
    private interface ChainSupplier<T> {
        T get();
    }
}
