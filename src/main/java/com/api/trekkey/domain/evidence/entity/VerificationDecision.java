package com.api.trekkey.domain.evidence.entity;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.AssuranceLevel;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationDecisionType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "evidence_verification_decision")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VerificationDecision extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, unique = true, updatable = false)
    private VerificationCase verificationCase;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private VerificationDecisionType decision;
    @Enumerated(EnumType.STRING)
    @Column(name = "assurance_level", nullable = false, length = 5, updatable = false)
    private AssuranceLevel assuranceLevel;
    @Column(name = "reason_code", nullable = false, length = 100, updatable = false)
    private String reasonCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_primary_id", nullable = false, updatable = false)
    private User primaryReviewer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_secondary_id", nullable = false, updatable = false)
    private User secondaryReviewer;
    @Column(name = "decided_at", nullable = false, updatable = false)
    private LocalDateTime decidedAt;
    @Column(name = "bundle_hash", nullable = false, length = 64, updatable = false)
    private String bundleHash;

    @PrePersist
    void assignPublicId() {
        if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString();
    }
}
