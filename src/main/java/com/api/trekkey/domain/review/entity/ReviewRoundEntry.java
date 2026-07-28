package com.api.trekkey.domain.review.entity;

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
        name = "review_round_entry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_round_entry_round_submission",
                columnNames = {"review_round_id", "submission_id"}))
public class ReviewRoundEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_round_id", nullable = false)
    private ReviewRound reviewRound;

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

    public boolean finalizeByRule(
            BigDecimal finalScore,
            int rankNo,
            ReviewRoundEntryStatus nextStatus,
            LocalDateTime finalizedAt
    ) {
        return finalizeResult(
                finalScore,
                rankNo,
                nextStatus,
                ReviewDecisionType.RULE,
                null,
                null,
                finalizedAt
        );
    }

    public boolean finalizeManually(
            BigDecimal finalScore,
            Integer rankNo,
            ReviewRoundEntryStatus nextStatus,
            User decidedByUser,
            String decisionReason,
            LocalDateTime finalizedAt
    ) {
        if (decidedByUser == null
                || decisionReason == null
                || decisionReason.isBlank()) {
            return false;
        }
        return finalizeResult(
                finalScore,
                rankNo,
                nextStatus,
                ReviewDecisionType.MANUAL,
                decidedByUser,
                decisionReason.trim(),
                finalizedAt
        );
    }

    public boolean isFinalized() {
        if (finalizedAt == null) {
            return false;
        }
        if (status == ReviewRoundEntryStatus.WITHDRAWN
                || status == ReviewRoundEntryStatus.DISQUALIFIED) {
            return true;
        }
        if (status != ReviewRoundEntryStatus.SELECTED
                && status != ReviewRoundEntryStatus.NOT_SELECTED) {
            return false;
        }
        if (decisionType == ReviewDecisionType.RULE) {
            return hasScoredResult();
        }
        if (decisionType != ReviewDecisionType.MANUAL
                || decidedByUser == null
                || decisionReason == null
                || decisionReason.isBlank()) {
            return false;
        }
        return hasValidManualRank(rankNo);
    }

    private boolean finalizeResult(
            BigDecimal finalScore,
            Integer rankNo,
            ReviewRoundEntryStatus nextStatus,
            ReviewDecisionType decisionType,
            User decidedByUser,
            String decisionReason,
            LocalDateTime finalizedAt
    ) {
        if (status != ReviewRoundEntryStatus.IN_REVIEW
                || finalizedAt == null
                || (nextStatus != ReviewRoundEntryStatus.SELECTED
                && nextStatus != ReviewRoundEntryStatus.NOT_SELECTED)
                || (decisionType != ReviewDecisionType.RULE
                && decisionType != ReviewDecisionType.MANUAL)
                || (decisionType == ReviewDecisionType.RULE
                && !hasValidScoreAndRank(finalScore, rankNo))
                || (decisionType == ReviewDecisionType.MANUAL
                && !hasValidManualRank(rankNo))) {
            return false;
        }
        this.finalScore = finalScore;
        this.rankNo = rankNo;
        this.status = nextStatus;
        this.decisionType = decisionType;
        this.decidedByUser = decidedByUser;
        this.decisionReason = decisionReason;
        this.finalizedAt = finalizedAt;
        return true;
    }

    private boolean hasScoredResult() {
        return hasValidScoreAndRank(finalScore, rankNo);
    }

    private boolean hasValidScoreAndRank(
            BigDecimal score,
            Integer rank
    ) {
        return score != null && rank != null && rank > 0;
    }

    private boolean hasValidManualRank(Integer rank) {
        return rank != null && rank > 0;
    }
}
