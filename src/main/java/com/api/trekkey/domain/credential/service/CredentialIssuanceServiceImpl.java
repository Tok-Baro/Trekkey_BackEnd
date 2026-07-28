package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.crypto.CanonicalJson;
import com.api.trekkey.domain.credential.crypto.CryptoValidationException;
import com.api.trekkey.domain.credential.crypto.FileManifest;
import com.api.trekkey.domain.credential.crypto.Hash32;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.crypto.SourceFingerprint;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialSource;
import com.api.trekkey.domain.credential.entity.AncCredentialSubject;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialSourceRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.IssuedCredential;
import com.api.trekkey.domain.credential.service.support.CredentialIssueValidator;
import com.api.trekkey.domain.credential.service.support.CredentialPayloadFactory;
import com.api.trekkey.domain.credential.service.support.UtcTime;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CredentialIssuanceServiceImpl implements CredentialIssuanceService {

    private final OrganizationRepository organizationRepository;
    private final AncCredentialRepository credentialRepository;
    private final AncCredentialSourceRepository credentialSourceRepository;
    private final AncCredentialSubjectRepository credentialSubjectRepository;

    @Override
    public IssuedCredential issue(CredentialIssueCommand command) {
        CredentialIssueValidator.validate(command);
        Organization organization = organizationRepository.findByIdForUpdate(command.issuerOrganizationId())
                .orElseThrow(() -> new CustomException(OrganizationErrorResponseCode.ORGANIZATION_NOT_FOUND));
        String issuerPublicId = organization.ensurePublicId();

        try {
            FileManifest.Result manifest = fileManifest(command.files());
            SourceFingerprint.Result sourceFingerprint = sourceFingerprint(command, issuerPublicId);
            AncCredential existing = findExisting(sourceFingerprint.fingerprintHash());
            if (existing != null) {
                if (!matchesIssuanceRequest(existing, command, manifest)) {
                    throw new CustomException(CredentialErrorResponseCode.CREDENTIAL_SOURCE_CONFLICT);
                }
                return response(existing, true);
            }
            if (credentialRepository.findByIssuerOrganizationIdAndCredentialNo(
                    organization.getId(), command.credentialNo()).isPresent()) {
                throw new CustomException(CredentialErrorResponseCode.CREDENTIAL_NUMBER_ALREADY_EXISTS);
            }

            String credentialPublicId = UUID.randomUUID().toString();
            CredentialIssueCommand publicCommand = withOpaqueUserSubjectRefs(command);
            byte[] canonicalBytes = CanonicalJson.canonicalize(CredentialPayloadFactory.create(
                    credentialPublicId,
                    publicCommand,
                    issuerPublicId,
                    organization.getName(),
                    manifest.hash().hex()));
            Hash32 contentHash = Hashing.sha256(canonicalBytes);
            Hash32 issuerId = Hashing.issuerId(issuerPublicId);
            Hash32 credentialIdHash = Hashing.credentialId(issuerId, credentialPublicId);
            Hash32 schemaVersionHash = Hashing.schemaVersion(command.schemaProfileId());

            AncCredential credential = credentialRepository.save(AncCredential.ready(
                    organization.getId(),
                    credentialPublicId,
                    credentialIdHash.bytes(),
                    command.credentialNo(),
                    command.credentialType(),
                    command.schemaProfileId(),
                    schemaVersionHash.bytes(),
                    new String(canonicalBytes, StandardCharsets.UTF_8),
                    canonicalBytes,
                    manifest.canonicalBytes(),
                    contentHash.bytes(),
                    manifest.hash().bytes(),
                    UtcTime.toLocalDateTime(command.issuedAt()),
                    command.expiresAt() == null ? null : UtcTime.toLocalDateTime(command.expiresAt())));

            credentialSourceRepository.save(sourceEntity(credential, command, sourceFingerprint));
            credentialSubjectRepository.saveAll(subjectEntities(credential, publicCommand));
            return response(credential, false);
        } catch (CryptoValidationException exception) {
            throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
        }
    }

    private AncCredential findExisting(Hash32 sourceFingerprint) {
        return credentialSourceRepository.findBySourceFingerprint(sourceFingerprint.bytes())
                .flatMap(source -> credentialRepository.findById(source.getCredentialId()))
                .orElse(null);
    }

    private boolean matchesIssuanceRequest(
            AncCredential credential,
            CredentialIssueCommand command,
            FileManifest.Result manifest) {
        return credential.getCredentialNo().equals(command.credentialNo())
                && credential.getCredentialType() == command.credentialType()
                && credential.getSchemaProfileId().equals(command.schemaProfileId())
                && UtcTime.toInstant(credential.getIssuedAt()).equals(command.issuedAt())
                && Objects.equals(
                        credential.getExpiresAt() == null ? null : UtcTime.toInstant(credential.getExpiresAt()),
                        command.expiresAt())
                && MessageDigest.isEqual(credential.getFileManifestHash(), manifest.hash().bytes());
    }

    private SourceFingerprint.Result sourceFingerprint(
            CredentialIssueCommand command,
            String issuerPublicId) {
        ObjectNode sourceSnapshot = JsonNodeFactory.instance.objectNode();
        sourceSnapshot.put("finalizedAt", command.source().finalizedAt().toString());
        sourceSnapshot.set("snapshot", command.source().snapshot());

        List<SourceFingerprint.SubjectSnapshot> subjects =
                CredentialPayloadFactory.sortedSubjects(command.subjects()).stream()
                        .map(this::fingerprintSubject)
                        .toList();
        return SourceFingerprint.build(new SourceFingerprint.Input(
                issuerPublicId,
                command.credentialType().name(),
                command.source().sourceType().name(),
                command.source().publicId(),
                sourceSnapshot,
                subjects,
                command.schemaProfileId()));
    }

    private SourceFingerprint.SubjectSnapshot fingerprintSubject(CredentialIssueCommand.Subject subject) {
        ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
        snapshot.put("displayName", subject.displayName());
        if (subject.major() == null) {
            snapshot.putNull("major");
        } else {
            snapshot.put("major", subject.major());
        }
        snapshot.put("subjectType", subject.subjectType().name());
        snapshot.put("disclosureClass", subject.disclosureClass().name());
        return new SourceFingerprint.SubjectSnapshot(subject.subjectRef(), subject.roleCode(), snapshot);
    }

    private CredentialIssueCommand withOpaqueUserSubjectRefs(CredentialIssueCommand command) {
        List<CredentialIssueCommand.Subject> publicSubjects = command.subjects().stream()
                .map(subject -> subject.subjectType() == CredentialSubjectType.USER
                        ? new CredentialIssueCommand.Subject(
                                subject.userId(),
                                subject.teamId(),
                                "user:" + UUID.randomUUID(),
                                subject.subjectType(),
                                subject.displayName(),
                                subject.major(),
                                subject.roleCode(),
                                subject.disclosureClass(),
                                subject.order())
                        : subject)
                .toList();
        return new CredentialIssueCommand(
                command.issuerOrganizationId(),
                command.credentialNo(),
                command.credentialType(),
                command.schemaProfileId(),
                command.source(),
                publicSubjects,
                command.files(),
                command.issuedAt(),
                command.expiresAt());
    }

    private FileManifest.Result fileManifest(List<CredentialIssueCommand.FileEvidence> files) {
        return FileManifest.build(files.stream()
                .map(file -> new FileManifest.Entry(
                        file.originalName(),
                        file.contentType(),
                        file.sizeBytes(),
                        Hash32.fromHex(file.sha256Hex())))
                .toList());
    }

    private AncCredentialSource sourceEntity(
            AncCredential credential,
            CredentialIssueCommand command,
            SourceFingerprint.Result sourceFingerprint) {
        CredentialIssueCommand.Source source = command.source();
        return AncCredentialSource.of(
                credential.getId(),
                source.sourceType(),
                source.teamId(),
                source.submissionId(),
                source.awardId(),
                source.publicId(),
                sourceFingerprint.fingerprintHash().bytes(),
                UtcTime.toLocalDateTime(source.finalizedAt()));
    }

    private List<AncCredentialSubject> subjectEntities(
            AncCredential credential,
            CredentialIssueCommand command) {
        return CredentialPayloadFactory.sortedSubjects(command.subjects()).stream()
                .map(subject -> AncCredentialSubject.of(
                        credential.getId(),
                        subject.userId(),
                        subject.teamId(),
                        subject.subjectRef(),
                        subject.subjectType(),
                        subject.displayName(),
                        subject.major(),
                        subject.roleCode(),
                        subject.disclosureClass(),
                        subject.order()))
                .toList();
    }

    private IssuedCredential response(AncCredential credential, boolean alreadyExisted) {
        return new IssuedCredential(
                credential.getPublicId(),
                credential.getCredentialNo(),
                credential.getStatus(),
                UtcTime.toInstant(credential.getIssuedAt()),
                alreadyExisted);
    }
}
