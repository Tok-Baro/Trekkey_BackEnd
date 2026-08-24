package com.api.trekkey.domain.evidence.entity;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.FileSafetyStatus;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "evidence_file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_evidence_file_submission_hash", columnNames = {"submission_id", "sha256"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EvidenceFile extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private EvidenceSubmission submission;
    @Column(name = "original_name", nullable = false, length = 255, updatable = false)
    private String originalName;
    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;
    @Column(name = "storage_key", nullable = false, unique = true, length = 500, updatable = false)
    private String storageKey;
    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;
    @Enumerated(EnumType.STRING)
    @Column(name = "safety_status", nullable = false, length = 30, updatable = false)
    private FileSafetyStatus safetyStatus;

    @PrePersist
    void assignPublicId() {
        if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString();
    }
}
