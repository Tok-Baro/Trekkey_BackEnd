package com.api.trekkey.domain.evidence.entity;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.AssuranceLevel;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationCaseStatus;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "evidence_verification_case", indexes =
        @Index(name = "idx_evidence_case_queue", columnList = "status,opened_at"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VerificationCase extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, unique = true, updatable = false)
    private EvidenceSubmission submission;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VerificationCaseStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "required_assurance_level", nullable = false, length = 5, updatable = false)
    private AssuranceLevel requiredAssuranceLevel;
    @Enumerated(EnumType.STRING)
    @Column(name = "achieved_assurance_level", length = 5)
    private AssuranceLevel achievedAssuranceLevel;
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Version @Column(nullable = false)
    private Long version;

    @PrePersist
    void assignPublicId() {
        if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString();
    }

    public void awaitSecondReview() { status = VerificationCaseStatus.AWAITING_SECOND_REVIEW; }
    public void close(VerificationCaseStatus finalStatus, AssuranceLevel achieved, LocalDateTime now) {
        status = finalStatus;
        achievedAssuranceLevel = achieved;
        closedAt = now;
    }

    public boolean isClosed() {
        return status == VerificationCaseStatus.VERIFIED || status == VerificationCaseStatus.REJECTED
                || status == VerificationCaseStatus.INCONCLUSIVE;
    }
}
