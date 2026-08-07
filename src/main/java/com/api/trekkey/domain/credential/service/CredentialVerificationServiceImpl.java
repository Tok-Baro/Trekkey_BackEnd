package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.config.BlockchainProperties;
import com.api.trekkey.domain.credential.crypto.CanonicalJson;
import com.api.trekkey.domain.credential.crypto.CredentialLeaf;
import com.api.trekkey.domain.credential.crypto.CryptoValidationException;
import com.api.trekkey.domain.credential.crypto.EthereumAddress;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.crypto.StandardMerkleTree;
import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncBatchItem;
import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.BatchStatus;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncBatchItemRepository;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncChainTransactionRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncIssuerKeyRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationStatus;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.domain.credential.service.port.BlockchainAnchorPort;
import com.api.trekkey.domain.credential.service.port.BlockchainGatewayException;
import com.api.trekkey.domain.credential.service.support.UtcTime;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialVerificationServiceImpl implements CredentialVerificationService {

    private final AncCredentialRepository credentialRepository;
    private final AncBatchItemRepository batchItemRepository;
    private final AncBatchRepository batchRepository;
    private final AncIssuerKeyRepository issuerKeyRepository;
    private final AncChainTransactionRepository chainTransactionRepository;
    private final OrganizationRepository organizationRepository;
    private final BlockchainAnchorPort blockchainAnchorPort;
    private final BlockchainProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock credentialClock;

    @Override
    public CredentialVerificationView verify(String credentialPublicId) {
        AncCredential credential = credentialRepository.findByPublicId(credentialPublicId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        Organization organization = organizationRepository.findById(credential.getIssuerOrganizationId())
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));

        LocalEvidence localEvidence;
        try {
            localEvidence = verifyLocalEvidence(credential, organization);
        } catch (IllegalArgumentException exception) {
            return response(
                    CredentialVerificationStatus.TAMPERED,
                    credential,
                    LocalEvidence.empty(),
                    null,
                    null);
        }
        if (!localEvidence.allValid()) {
            return response(
                    CredentialVerificationStatus.TAMPERED,
                    credential,
                    localEvidence,
                    null,
                    null);
        }
        if (!CredentialSchemaProfiles.supports(
                localEvidence.payload().credentialType(),
                localEvidence.payload().schemaProfileId())) {
            return response(
                    CredentialVerificationStatus.SCHEMA_UNSUPPORTED,
                    credential,
                    localEvidence,
                    null,
                    null);
        }
        if (localEvidence.batch() == null) {
            return response(
                    CredentialVerificationStatus.PENDING,
                    credential,
                    localEvidence,
                    null,
                    null);
        }
        if (!properties.isReadEnabled()) {
            CredentialVerificationStatus status = localEvidence.batch().getStatus() == BatchStatus.ANCHORED
                    ? CredentialVerificationStatus.RPC_UNAVAILABLE
                    : CredentialVerificationStatus.PENDING;
            return response(status, credential, localEvidence, null, null);
        }

        try {
            BlockchainAnchorPort.OnChainBatch onChainBatch =
                    blockchainAnchorPort.getBatch(Hash32.of(localEvidence.batch().getBatchIdHash()));
            if (!onChainBatch.exists()) {
                CredentialVerificationStatus status =
                        localEvidence.batch().getStatus() == BatchStatus.ANCHORED
                                ? CredentialVerificationStatus.ANCHOR_NOT_FOUND
                                : CredentialVerificationStatus.PENDING;
                return response(status, credential, localEvidence, null, null);
            }
            if (!batchMatches(localEvidence, onChainBatch)) {
                return response(
                        CredentialVerificationStatus.TAMPERED,
                        credential,
                        localEvidence,
                        null,
                        null);
            }
            if (!issuerWasValid(localEvidence.batch(), onChainBatch)) {
                return response(
                        CredentialVerificationStatus.ISSUER_INVALID,
                        credential,
                        localEvidence,
                        null,
                        null);
            }

            BlockchainAnchorPort.OnChainCredentialStatus chainStatus = blockchainAnchorPort.getCredentialStatus(
                    localEvidence.issuerId(),
                    localEvidence.credentialIdHash());
            if (chainStatus.state() != BlockchainAnchorPort.CredentialChainState.NONE
                    && !statusIssuerWasValid(localEvidence.issuerId(), chainStatus)) {
                return response(
                        CredentialVerificationStatus.ISSUER_INVALID,
                        credential,
                        localEvidence,
                        null,
                        null);
            }
            if (chainStatus.state() == BlockchainAnchorPort.CredentialChainState.REVOKED) {
                return response(
                        CredentialVerificationStatus.REVOKED,
                        credential,
                        localEvidence,
                        null,
                        null);
            }
            if (chainStatus.state() == BlockchainAnchorPort.CredentialChainState.SUPERSEDED) {
                String replacementHash = chainStatus.replacementCredentialIdHash().hex();
                String replacementPublicId = credentialRepository
                        .findByCredentialIdHash(chainStatus.replacementCredentialIdHash().bytes())
                        .map(AncCredential::getPublicId)
                        .orElse(null);
                return response(
                        CredentialVerificationStatus.SUPERSEDED,
                        credential,
                        localEvidence,
                        replacementPublicId,
                        replacementHash);
            }
            if (localEvidence.payload().expiresAt() != null
                    && !credentialClock.instant().isBefore(localEvidence.payload().expiresAt())) {
                return response(
                        CredentialVerificationStatus.EXPIRED,
                        credential,
                        localEvidence,
                        null,
                        null);
            }
            return response(
                    CredentialVerificationStatus.VALID,
                    credential,
                    localEvidence,
                    null,
                    null);
        } catch (BlockchainGatewayException exception) {
            return response(
                    exception.isRetryable()
                            ? CredentialVerificationStatus.RPC_UNAVAILABLE
                            : CredentialVerificationStatus.BLOCKCHAIN_CONFIGURATION_ERROR,
                    credential,
                    localEvidence,
                    null,
                    null);
        }
    }

    private LocalEvidence verifyLocalEvidence(AncCredential credential, Organization organization) {
        String organizationPublicId = organization.getPublicId();
        if (organizationPublicId == null || organizationPublicId.isBlank()) {
            throw new CryptoValidationException("organization public ID is missing");
        }

        byte[] canonicalBytes = credential.getCanonicalBytes();
        VerifiedPayload payload = parsePayload(canonicalBytes);
        byte[] recanonicalized = CanonicalJson.canonicalize(credential.getPayloadJson());
        boolean canonicalMatches = MessageDigest.isEqual(recanonicalized, canonicalBytes);
        boolean contentHashMatches = Hashing.sha256(canonicalBytes)
                .equals(Hash32.of(credential.getContentHash()));
        boolean manifestHashMatches = Hashing.sha256(credential.getFileManifestCanonicalBytes())
                .equals(Hash32.of(credential.getFileManifestHash()));
        boolean claimsMatch = claimsMatch(credential, organizationPublicId, payload);

        Hash32 issuerId = Hashing.issuerId(payload.issuerPublicId());
        Hash32 credentialIdHash = Hashing.credentialId(issuerId, payload.credentialPublicId());
        boolean credentialIdMatches = credentialIdHash.equals(Hash32.of(credential.getCredentialIdHash()));

        AncBatchItem item = batchItemRepository.findByCredentialId(credential.getId()).orElse(null);
        if (item == null) {
            boolean proofMatches = credential.getStatus() == CredentialStatus.READY;
            return new LocalEvidence(
                    canonicalMatches,
                    contentHashMatches,
                    manifestHashMatches,
                    claimsMatch,
                    credentialIdMatches,
                    proofMatches,
                    payload,
                    issuerId,
                    credentialIdHash,
                    null,
                    null,
                    List.of(),
                    null);
        }
        AncBatch batch = batchRepository.findById(item.getBatchId())
                .orElseThrow(() -> new CryptoValidationException("batch evidence is missing"));
        CredentialLeaf leaf = new CredentialLeaf(
                issuerId,
                credentialIdHash,
                Hash32.of(credential.getSchemaVersionHash()),
                Hash32.of(credential.getContentHash()),
                Hash32.of(credential.getFileManifestHash()));
        List<String> proofHex = proof(item.getMerkleProofJson());
        List<Hash32> proof = proofHex.stream().map(Hash32::fromHex).toList();
        boolean proofMatches = MessageDigest.isEqual(item.getCredentialIdHash(), credentialIdHash.bytes())
                && leaf.hash().equals(Hash32.of(item.getLeafHash()))
                && StandardMerkleTree.verify(Hash32.of(batch.getMerkleRoot()), leaf.hash(), proof);
        return new LocalEvidence(
                canonicalMatches,
                contentHashMatches,
                manifestHashMatches,
                claimsMatch,
                credentialIdMatches,
                proofMatches,
                payload,
                issuerId,
                credentialIdHash,
                batch,
                leaf.hash(),
                proofHex,
                transaction(batch.getId()));
    }

    private VerifiedPayload parsePayload(byte[] canonicalBytes) {
        try {
            JsonNode root = objectMapper.readTree(canonicalBytes);
            if (root == null || !root.isObject()) {
                throw new CryptoValidationException("Credential payload must be an object");
            }
            String credentialPublicId = requiredText(root, "credentialId");
            String credentialNo = requiredText(root, "credentialNo");
            CredentialType credentialType = CredentialType.valueOf(requiredText(root, "credentialType"));
            String schemaProfileId = requiredText(root, "schemaProfileId");
            Instant issuedAt = instant(root, "issuedAt", false);
            Instant expiresAt = instant(root, "expiresAt", true);
            String fileManifestHash = requiredText(root, "fileManifestHash");

            JsonNode issuer = requiredObject(root, "issuer");
            String issuerPublicId = requiredText(issuer, "publicId");
            String issuerName = requiredText(issuer, "name");

            JsonNode subjects = root.get("subjects");
            if (subjects == null || !subjects.isArray() || subjects.isEmpty()) {
                throw new CryptoValidationException("Credential subjects must be a non-empty array");
            }
            List<CredentialVerificationView.PublicSubject> publicSubjects = new ArrayList<>();
            for (JsonNode subject : subjects) {
                if (!subject.isObject()) {
                    throw new CryptoValidationException("Credential subject must be an object");
                }
                String subjectRef = requiredText(subject, "ref");
                String subjectType = CredentialSubjectType.valueOf(requiredText(subject, "type")).name();
                String displayName = requiredText(subject, "displayName");
                String major = nullableText(subject, "major");
                String roleCode = requiredText(subject, "roleCode");
                DisclosureClass disclosureClass =
                        DisclosureClass.valueOf(requiredText(subject, "disclosureClass"));
                if (disclosureClass == DisclosureClass.PUBLIC) {
                    publicSubjects.add(new CredentialVerificationView.PublicSubject(
                            subjectRef,
                            subjectType,
                            displayName,
                            major,
                            roleCode));
                }
            }
            return new VerifiedPayload(
                    credentialPublicId,
                    credentialNo,
                    credentialType,
                    schemaProfileId,
                    issuerPublicId,
                    issuerName,
                    issuedAt,
                    expiresAt,
                    fileManifestHash,
                    List.copyOf(publicSubjects));
        } catch (CryptoValidationException | DateTimeParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CryptoValidationException("Credential payload cannot be parsed", exception);
        }
    }

    private boolean claimsMatch(
            AncCredential credential,
            String organizationPublicId,
            VerifiedPayload payload) {
        Instant expiresAt = credential.getExpiresAt() == null
                ? null
                : UtcTime.toInstant(credential.getExpiresAt());
        return payload.credentialPublicId().equals(credential.getPublicId())
                && payload.credentialNo().equals(credential.getCredentialNo())
                && payload.credentialType() == credential.getCredentialType()
                && payload.schemaProfileId().equals(credential.getSchemaProfileId())
                && payload.issuerPublicId().equals(organizationPublicId)
                && UtcTime.samePersistedInstant(
                        payload.issuedAt(),
                        UtcTime.toInstant(credential.getIssuedAt()))
                && UtcTime.samePersistedInstant(payload.expiresAt(), expiresAt)
                && payload.fileManifestHash().equals(Hash32.of(credential.getFileManifestHash()).hex())
                && Hashing.schemaVersion(payload.schemaProfileId())
                        .equals(Hash32.of(credential.getSchemaVersionHash()));
    }

    private boolean batchMatches(
            LocalEvidence evidence,
            BlockchainAnchorPort.OnChainBatch actual) {
        AncBatch expected = evidence.batch();
        AncIssuerKey key = issuerKeyRepository.findById(expected.getIssuerKeyId()).orElse(null);
        return key != null
                && actual.issuerId().equals(evidence.issuerId())
                && actual.merkleRoot().equals(Hash32.of(expected.getMerkleRoot()))
                && actual.schemaVersionHash().equals(Hash32.of(expected.getSchemaVersionHash()))
                && actual.leafCount() == expected.getLeafCount()
                && actual.treeVersion() == expected.getTreeVersion()
                && actual.issuerKeyVersion() == key.getKeyVersion();
    }

    private boolean issuerWasValid(
            AncBatch batch,
            BlockchainAnchorPort.OnChainBatch onChainBatch) {
        AncIssuerKey expectedKey = issuerKeyRepository.findById(batch.getIssuerKeyId()).orElse(null);
        if (expectedKey == null) {
            return false;
        }
        BlockchainAnchorPort.OnChainIssuerKey actualKey =
                blockchainAnchorPort.getIssuerKey(onChainBatch.issuerId(), onChainBatch.issuerKeyVersion());
        long anchoredAt = onChainBatch.anchoredAt();
        return actualKey.exists()
                && actualKey.signer().equals(EthereumAddress.fromHex(hex(expectedKey.getSignerAddress())))
                && anchoredAt >= actualKey.validFrom()
                && (actualKey.validUntil() == 0 || anchoredAt <= actualKey.validUntil())
                && (actualKey.compromisedAt() == 0 || anchoredAt < actualKey.compromisedAt());
    }

    private boolean statusIssuerWasValid(
            Hash32 issuerId,
            BlockchainAnchorPort.OnChainCredentialStatus status) {
        if (status.recordedAt() <= 0 || status.recordedAt() < status.effectiveAt()) {
            return false;
        }
        BlockchainAnchorPort.OnChainIssuerKey key =
                blockchainAnchorPort.getIssuerKey(issuerId, status.issuerKeyVersion());
        return key.exists()
                && status.recordedAt() >= key.validFrom()
                && (key.validUntil() == 0 || status.recordedAt() <= key.validUntil())
                && (key.compromisedAt() == 0 || status.recordedAt() < key.compromisedAt());
    }

    private List<String> proof(String proofJson) {
        try {
            return objectMapper.readValue(proofJson, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new CryptoValidationException("Merkle proof JSON is invalid", exception);
        }
    }

    private AncChainTransaction transaction(Long batchId) {
        if (batchId == null) {
            return null;
        }
        return chainTransactionRepository.findByBatchIdAndOperationType(batchId, ChainOperationType.ANCHOR_BATCH)
                .orElse(null);
    }

    private CredentialVerificationView response(
            CredentialVerificationStatus status,
            AncCredential credential,
            LocalEvidence local,
            String replacementPublicId,
            String replacementHash) {
        VerifiedPayload payload = local.allValid() ? local.payload() : null;
        AncBatch batch = local.batch();
        AncChainTransaction transaction = local.transaction();
        CredentialVerificationView.Evidence evidence = new CredentialVerificationView.Evidence(
                local.canonicalPayloadMatches(),
                local.contentHashMatches(),
                local.fileManifestHashMatches(),
                local.credentialClaimsMatch(),
                local.credentialIdMatches(),
                local.merkleProofMatches(),
                hex(local.issuerId()),
                hex(local.credentialIdHash()),
                safeHash(credential.getSchemaVersionHash(), local),
                safeHash(credential.getContentHash(), local),
                safeHash(credential.getFileManifestHash(), local),
                hex(local.leafHash()),
                batch == null ? null : batch.getPublicId(),
                batch == null ? null : Hash32.of(batch.getBatchIdHash()).hex(),
                batch == null ? null : Hash32.of(batch.getMerkleRoot()).hex(),
                batch == null ? null : batch.getTreeVersion(),
                local.proof(),
                properties.getChainId(),
                properties.getContractAddress(),
                transaction == null || transaction.getTxHash() == null
                        ? null
                        : Hash32.of(transaction.getTxHash()).hex(),
                transaction == null ? null : transaction.getBlockNumber());
        return new CredentialVerificationView(
                status,
                credential.getPublicId(),
                payload == null ? null : payload.credentialNo(),
                payload == null ? null : payload.credentialType(),
                payload == null ? null : payload.schemaProfileId(),
                payload == null ? null : payload.issuerPublicId(),
                payload == null ? null : payload.issuerName(),
                payload == null ? null : payload.issuedAt(),
                payload == null ? null : payload.expiresAt(),
                payload == null ? List.of() : payload.publicSubjects(),
                evidence,
                replacementPublicId,
                replacementHash);
    }

    private JsonNode requiredObject(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isObject()) {
            throw new CryptoValidationException(fieldName + " must be an object");
        }
        return value;
    }

    private String requiredText(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new CryptoValidationException(fieldName + " must be a non-blank string");
        }
        return value.textValue();
    }

    private String nullableText(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new CryptoValidationException(fieldName + " must be null or a non-blank string");
        }
        return value.textValue();
    }

    private Instant instant(JsonNode parent, String fieldName, boolean nullable) {
        JsonNode value = parent.get(fieldName);
        if (nullable && (value == null || value.isNull())) {
            return null;
        }
        return Instant.parse(requiredText(parent, fieldName));
    }

    private String safeHash(byte[] value, LocalEvidence evidence) {
        return evidence.payload() == null ? null : Hash32.of(value).hex();
    }

    private String hex(Hash32 value) {
        return value == null ? null : value.hex();
    }

    private String hex(byte[] value) {
        StringBuilder result = new StringBuilder("0x");
        for (byte current : value) {
            result.append(Character.forDigit((current >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(current & 0x0f, 16));
        }
        return result.toString();
    }

    private record VerifiedPayload(
            String credentialPublicId,
            String credentialNo,
            CredentialType credentialType,
            String schemaProfileId,
            String issuerPublicId,
            String issuerName,
            Instant issuedAt,
            Instant expiresAt,
            String fileManifestHash,
            List<CredentialVerificationView.PublicSubject> publicSubjects) {
    }

    private record LocalEvidence(
            boolean canonicalPayloadMatches,
            boolean contentHashMatches,
            boolean fileManifestHashMatches,
            boolean credentialClaimsMatch,
            boolean credentialIdMatches,
            boolean merkleProofMatches,
            VerifiedPayload payload,
            Hash32 issuerId,
            Hash32 credentialIdHash,
            AncBatch batch,
            Hash32 leafHash,
            List<String> proof,
            AncChainTransaction transaction) {

        static LocalEvidence empty() {
            return new LocalEvidence(
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null);
        }

        boolean allValid() {
            return canonicalPayloadMatches
                    && contentHashMatches
                    && fileManifestHashMatches
                    && credentialClaimsMatch
                    && credentialIdMatches
                    && merkleProofMatches;
        }
    }
}
