package com.api.trekkey.domain.credential.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "anc_credential_source",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_anc_credential_source_fingerprint",
                columnNames = "source_fingerprint"),
        indexes = {
                @Index(name = "idx_anc_credential_source_team", columnList = "team_id"),
                @Index(name = "idx_anc_credential_source_submission", columnList = "submission_id"),
                @Index(name = "idx_anc_credential_source_award", columnList = "award_id")
        })
public class AncCredentialSource {

    @Id
    @Column(name = "credential_id")
    private Long credentialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, updatable = false)
    private CredentialSourceType sourceType;

    @Column(name = "team_id", updatable = false)
    private Long teamId;

    @Column(name = "submission_id", updatable = false)
    private Long submissionId;

    @Column(name = "award_id", updatable = false)
    private Long awardId;

    @Column(name = "source_public_id", nullable = false, length = 64, updatable = false)
    private String sourcePublicId;

    @Getter(AccessLevel.NONE)
    @Column(name = "source_fingerprint", nullable = false, length = 32, updatable = false)
    private byte[] sourceFingerprint;

    @Column(name = "source_finalized_at", nullable = false, updatable = false)
    private LocalDateTime sourceFinalizedAt;

    private AncCredentialSource(
            Long credentialId,
            CredentialSourceType sourceType,
            Long teamId,
            Long submissionId,
            Long awardId,
            String sourcePublicId,
            byte[] sourceFingerprint,
            LocalDateTime sourceFinalizedAt) {
        if (credentialId == null || credentialId <= 0 || sourceType == null || isBlank(sourcePublicId)
                || sourceFinalizedAt == null
                || sourceFingerprint == null || sourceFingerprint.length != 32) {
            throw new IllegalArgumentException("credential source immutable fields are required");
        }
        validateSourceReference(sourceType, teamId, submissionId, awardId);
        this.credentialId = credentialId;
        this.sourceType = sourceType;
        this.teamId = teamId;
        this.submissionId = submissionId;
        this.awardId = awardId;
        this.sourcePublicId = sourcePublicId;
        this.sourceFingerprint = sourceFingerprint.clone();
        this.sourceFinalizedAt = sourceFinalizedAt;
    }

    public static AncCredentialSource of(
            Long credentialId,
            CredentialSourceType sourceType,
            Long teamId,
            Long submissionId,
            Long awardId,
            String sourcePublicId,
            byte[] sourceFingerprint,
            LocalDateTime sourceFinalizedAt) {
        return new AncCredentialSource(
                credentialId, sourceType, teamId, submissionId, awardId, sourcePublicId, sourceFingerprint,
                sourceFinalizedAt);
    }

    public byte[] getSourceFingerprint() {
        return sourceFingerprint == null ? null : sourceFingerprint.clone();
    }

    private static void validateSourceReference(
            CredentialSourceType sourceType, Long teamId, Long submissionId, Long awardId) {
        int referenceCount = positiveReference(teamId) + positiveReference(submissionId) + positiveReference(awardId);
        boolean typeMatches = switch (sourceType) {
            case TEAM -> teamId != null;
            case SUBMISSION -> submissionId != null;
            case AWARD -> awardId != null;
        };
        if (referenceCount != 1 || !typeMatches) {
            throw new IllegalArgumentException("credential source must have exactly one matching source reference");
        }
    }

    private static int positiveReference(Long reference) {
        if (reference != null && reference <= 0) {
            throw new IllegalArgumentException("source reference ids must be positive");
        }
        return reference == null ? 0 : 1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
