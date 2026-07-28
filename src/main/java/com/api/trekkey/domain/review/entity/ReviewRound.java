package com.api.trekkey.domain.review.entity;

import com.api.trekkey.domain.contest.entity.Contest;
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
import java.util.Objects;
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
        name = "review_round",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_round_contest_round_no",
                columnNames = {"contest_id", "round_no"}))
public class ReviewRound extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewRoundStatus status;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ReviewRoundTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_rule", nullable = false, length = 30)
    private ReviewRoundDecisionRule decisionRule;

    @Column(name = "select_count")
    private Integer selectCount;

    @Column(name = "min_score", precision = 12, scale = 2)
    private BigDecimal minScore;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    public void updateConfiguration(
            String name,
            int roundNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            ReviewRoundTargetType targetType,
            ReviewRoundDecisionRule decisionRule,
            Integer selectCount,
            BigDecimal minScore
    ) {
        this.name = name;
        this.roundNo = roundNo;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.targetType = targetType;
        this.decisionRule = decisionRule;
        this.selectCount = selectCount;
        this.minScore = minScore;
    }

    public boolean isConfigurationEditable() {
        return status == ReviewRoundStatus.PREPARING;
    }

    public boolean isManualWithoutReview() {
        return targetType == ReviewRoundTargetType.MANUAL
                && decisionRule == ReviewRoundDecisionRule.MANUAL;
    }

    public boolean hasValidConfigurationForOpening() {
        if (roundNo < 1
                || name == null
                || name.isBlank()
                || startsAt == null
                || endsAt == null
                || !startsAt.isBefore(endsAt)
                || targetType == null
                || decisionRule == null) {
            return false;
        }
        return switch (decisionRule) {
            case TOP_N -> selectCount != null
                    && selectCount > 0
                    && minScore == null;
            case MIN_SCORE -> minScore != null
                    && minScore.compareTo(BigDecimal.ZERO) >= 0
                    && selectCount == null;
            case MANUAL -> selectCount == null && minScore == null;
        };
    }

    public boolean hasSameConfiguration(
            String name,
            int roundNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            ReviewRoundTargetType targetType,
            ReviewRoundDecisionRule decisionRule,
            Integer selectCount,
            BigDecimal minScore
    ) {
        return Objects.equals(this.name, name)
                && this.roundNo == roundNo
                && Objects.equals(this.startsAt, startsAt)
                && Objects.equals(this.endsAt, endsAt)
                && this.targetType == targetType
                && this.decisionRule == decisionRule
                && Objects.equals(this.selectCount, selectCount)
                && hasSameDecimal(this.minScore, minScore);
    }

    public void moveToRoundNo(int roundNo) {
        this.roundNo = roundNo;
    }

    public boolean open() {
        if (status != ReviewRoundStatus.PREPARING
                || !hasValidConfigurationForOpening()) {
            return false;
        }
        status = ReviewRoundStatus.OPEN;
        return true;
    }

    public boolean finalizeAt(LocalDateTime finalizedAt) {
        if (status != ReviewRoundStatus.OPEN || finalizedAt == null) {
            return false;
        }
        status = ReviewRoundStatus.FINALIZED;
        this.finalizedAt = finalizedAt;
        return true;
    }

    public boolean isOpenAt(LocalDateTime now) {
        return now != null
                && status == ReviewRoundStatus.OPEN
                && !now.isBefore(startsAt)
                && now.isBefore(endsAt);
    }

    public boolean extendEndsAt(LocalDateTime newEndsAt) {
        if (status != ReviewRoundStatus.OPEN
                || newEndsAt == null
                || endsAt == null
                || !newEndsAt.isAfter(endsAt)) {
            return false;
        }
        endsAt = newEndsAt;
        return true;
    }

    private boolean hasSameDecimal(
            BigDecimal current,
            BigDecimal requested
    ) {
        if (current == null || requested == null) {
            return current == requested;
        }
        return current.compareTo(requested) == 0;
    }
}
