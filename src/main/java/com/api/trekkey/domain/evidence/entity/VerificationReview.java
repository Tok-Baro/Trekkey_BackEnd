package com.api.trekkey.domain.evidence.entity;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.AssuranceLevel;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.ReviewResult;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "evidence_verification_review", uniqueConstraints =
        @UniqueConstraint(name = "uk_evidence_review_case_reviewer", columnNames = {"case_id", "reviewer_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VerificationReview extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private VerificationCase verificationCase;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false, updatable = false)
    private User reviewer;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReviewResult result;
    @Enumerated(EnumType.STRING)
    @Column(name = "assurance_level", nullable = false, length = 5, updatable = false)
    private AssuranceLevel assuranceLevel;
    @Column(name = "reason_code", nullable = false, length = 100, updatable = false)
    private String reasonCode;
    @Column(length = 1000, updatable = false)
    private String note;
    @Column(name = "official_reference_url", length = 1000, updatable = false)
    private String officialReferenceUrl;
    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    void assignPublicId() {
        if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString();
    }
}
