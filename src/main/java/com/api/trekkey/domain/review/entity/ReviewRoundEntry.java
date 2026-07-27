package com.api.trekkey.domain.review.entity;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.user.entity.User;
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
import java.math.BigDecimal;
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
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_round_entry_stage_submission",
                columnNames = {"review_stage_id", "submission_id"}))
public class ReviewRoundEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_stage_id", nullable = false)
    private ContestStage reviewStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewRoundEntryStatus status;

    @Column(precision = 12, scale = 2)
    private BigDecimal finalScore;

    private Integer rankNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReviewDecisionType decisionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedByUser;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    private LocalDateTime finalizedAt;

    public boolean startReview() {
        if (status != ReviewRoundEntryStatus.ELIGIBLE) {
            return false;
        }
        status = ReviewRoundEntryStatus.IN_REVIEW;
        return true;
    }
}
