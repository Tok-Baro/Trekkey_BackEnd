package com.api.trekkey.domain.submission.entity;

import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_submission_public_id",
                        columnNames = "public_id"),
                @UniqueConstraint(
                        name = "uk_submission_team",
                        columnNames = "team_id")
        })
public class Submission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubmissionStatus status;

    private LocalDateTime finalizedAt;

    private LocalDateTime submittedAt;

    @PrePersist
    private void assignPublicId() {
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
    }

    public boolean isDraftEditable() {
        return finalizedAt == null && status == SubmissionStatus.DRAFT;
    }

    public boolean updateDraftTitle(String title) {
        if (!isDraftEditable()) {
            return false;
        }
        this.title = title;
        return true;
    }

    public boolean submit(LocalDateTime submittedAt) {
        if (submittedAt == null
                || isFinalized()
                || status == SubmissionStatus.WITHDRAWN) {
            return false;
        }
        if (status == SubmissionStatus.SUBMITTED) {
            return false;
        }
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = submittedAt;
        return true;
    }

    public boolean withdraw() {
        if (isFinalized()) {
            return false;
        }
        if (status == SubmissionStatus.WITHDRAWN) {
            return false;
        }
        if (status != SubmissionStatus.SUBMITTED) {
            return false;
        }
        this.status = SubmissionStatus.WITHDRAWN;
        return true;
    }

    public boolean reopenDraft() {
        if (isFinalized() || status == SubmissionStatus.WITHDRAWN) {
            return false;
        }
        if (status == SubmissionStatus.DRAFT) {
            return false;
        }
        this.status = SubmissionStatus.DRAFT;
        return true;
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }

    public boolean finalizeAt(LocalDateTime finalizedAt) {
        if (finalizedAt == null
                || isFinalized()
                || status != SubmissionStatus.SUBMITTED) {
            return false;
        }
        this.finalizedAt = finalizedAt;
        return true;
    }
}
