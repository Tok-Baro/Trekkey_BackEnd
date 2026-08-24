package com.api.trekkey.domain.evidence.entity;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.EvidenceStatus;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.EvidenceType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.NonCourseRecordType;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "evidence_submission", indexes = {
        @Index(name = "idx_evidence_submission_user", columnList = "submitted_by,created_at"),
        @Index(name = "idx_evidence_submission_org_status", columnList = "organization_id,status,created_at"),
        @Index(name = "idx_evidence_submission_duplicate", columnList = "issuer_code,credential_number_hash")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EvidenceSubmission extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false, updatable = false)
    private User submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 40, updatable = false)
    private EvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_record_type", nullable = false, length = 40, updatable = false)
    private NonCourseRecordType targetRecordType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvidenceStatus status;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(name = "issuer_name", nullable = false, length = 200, updatable = false)
    private String issuerName;

    @Column(name = "issuer_code", length = 100, updatable = false)
    private String issuerCode;

    @Column(name = "credential_number_hash", length = 64, updatable = false)
    private String credentialNumberHash;

    @Column(name = "credential_number_last4", length = 4, updatable = false)
    private String credentialNumberLast4;

    @Column(name = "numeric_value", precision = 10, scale = 2, updatable = false)
    private BigDecimal numericValue;

    @Column(name = "issued_at", updatable = false)
    private LocalDate issuedAt;

    @Column(name = "expires_at", updatable = false)
    private LocalDate expiresAt;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void assignPublicId() {
        if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString();
    }

    public void awaitSecondReview() { status = EvidenceStatus.AWAITING_SECOND_REVIEW; }
    public void verify() { status = EvidenceStatus.VERIFIED; }
    public void reject() { status = EvidenceStatus.REJECTED; }
    public void markInconclusive() { status = EvidenceStatus.INCONCLUSIVE; }
}
