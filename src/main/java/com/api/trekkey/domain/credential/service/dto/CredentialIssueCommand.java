package com.api.trekkey.domain.credential.service.dto;

import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * Boundary used by finalized contest-domain workflows. The credential service builds the
 * canonical envelope; callers provide only immutable business evidence and subject snapshots.
 */
public record CredentialIssueCommand(
        Long issuerOrganizationId,
        String credentialNo,
        CredentialType credentialType,
        String schemaProfileId,
        Source source,
        List<Subject> subjects,
        List<FileEvidence> files,
        Instant issuedAt,
        Instant expiresAt) {

    public CredentialIssueCommand {
        subjects = subjects == null ? null : List.copyOf(subjects);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public record Source(
            CredentialSourceType sourceType,
            Long teamId,
            Long submissionId,
            Long awardId,
            String publicId,
            Instant finalizedAt,
            JsonNode snapshot) {

        public Source {
            snapshot = snapshot == null ? null : snapshot.deepCopy();
        }

        @Override
        public JsonNode snapshot() {
            return snapshot == null ? null : snapshot.deepCopy();
        }
    }

    public record Subject(
            Long userId,
            Long teamId,
            String subjectRef,
            CredentialSubjectType subjectType,
            String displayName,
            String major,
            String roleCode,
            DisclosureClass disclosureClass,
            int order) {
    }

    public record FileEvidence(
            String originalName,
            String contentType,
            long sizeBytes,
            String sha256Hex) {
    }
}
