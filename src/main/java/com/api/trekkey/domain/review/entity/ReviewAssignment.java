package com.api.trekkey.domain.review.entity;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
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
        name = "review_assignment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_assignment_judge_entry",
                columnNames = {
                        "contest_judge_id",
                        "review_round_entry_id"
                }))
public class ReviewAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_judge_id", nullable = false)
    private ContestJudge contestJudge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_round_entry_id", nullable = false)
    private ReviewRoundEntry reviewRoundEntry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewAssignmentStatus status;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public boolean complete(LocalDateTime completedAt) {
        if (completedAt == null
                || status != ReviewAssignmentStatus.ASSIGNED) {
            return false;
        }
        this.status = ReviewAssignmentStatus.COMPLETED;
        this.completedAt = completedAt;
        return true;
    }

    public boolean cancel() {
        if (status != ReviewAssignmentStatus.ASSIGNED) {
            return false;
        }
        this.status = ReviewAssignmentStatus.CANCELED;
        return true;
    }

    public boolean reassign(
            LocalDateTime assignedAt,
            LocalDateTime dueAt
    ) {
        if (assignedAt == null
                || status != ReviewAssignmentStatus.CANCELED) {
            return false;
        }
        this.status = ReviewAssignmentStatus.ASSIGNED;
        this.assignedAt = assignedAt;
        this.dueAt = dueAt;
        this.completedAt = null;
        return true;
    }

    public boolean isAvailableAt(LocalDateTime now) {
        return status == ReviewAssignmentStatus.ASSIGNED
                && (dueAt == null || now.isBefore(dueAt));
    }
}
