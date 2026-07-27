package com.api.trekkey.domain.contest.entity;

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
        uniqueConstraints = @UniqueConstraint(
                name = "uk_contest_stage_sequence",
                columnNames = {"contest_id", "sequence_no"}))
public class ContestStage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 대회 단계 PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    // 소속 대회
    private Contest contest;

    @Column(nullable = false, length = 100)
    // 화면에 표시할 단계명
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    // 단계 유형: 신청, 제출, 심사, 발표, 시상
    private StageType stageType;

    @Column(nullable = false)
    // 대회 내 단계 진행 순서
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    // 단계 진행 상태
    private StageStatus status;

    // 단계 시작 시각
    private LocalDateTime startsAt;

    // 단계 종료 또는 마감 시각
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    // 평가 대상 선정 방식
    private StageTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    // 평가 단계의 통과 방식
    private StagePassRule passRule;

    // 상위 N팀 통과 시 다음 단계 진출 팀 수
    private Integer passCount;

    @Column(precision = 10, scale = 2)
    // 기준 점수 통과 시 필요한 최소 점수
    private BigDecimal minScore;

    // 상태 전이는 별도 API에서 처리하고, 대회 편집에서는 단계 설정만 갱신한다.
    public void updateConfiguration(
            String name,
            StageType stageType,
            int sequenceNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            StageTargetType targetType,
            StagePassRule passRule,
            Integer passCount,
            BigDecimal minScore
    ) {
        this.name = name;
        this.stageType = stageType;
        this.sequenceNo = sequenceNo;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.targetType = targetType;
        this.passRule = passRule;
        this.passCount = passCount;
        this.minScore = minScore;
    }

    public boolean isConfigurationEditable() {
        return status == StageStatus.PREPARING;
    }

    public void moveToSequence(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public boolean hasValidConfigurationForOpening() {
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            return false;
        }
        if (stageType == StageType.SUBMISSION && endsAt == null) {
            return false;
        }
        if (!stageType.supportsReviewCriteria()) {
            return true;
        }
        if (targetType == null || passRule == null) {
            return false;
        }
        return switch (passRule) {
            case TOP_N -> passCount != null && passCount > 0;
            case MIN_SCORE -> minScore != null && minScore.compareTo(BigDecimal.ZERO) >= 0;
            case MANUAL, FINAL -> true;
        };
    }

    public boolean hasSameConfiguration(
            String name,
            StageType stageType,
            int sequenceNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            StageTargetType targetType,
            StagePassRule passRule,
            Integer passCount,
            BigDecimal minScore
    ) {
        return Objects.equals(this.name, name)
                && this.stageType == stageType
                && this.sequenceNo == sequenceNo
                && Objects.equals(this.startsAt, startsAt)
                && Objects.equals(this.endsAt, endsAt)
                && this.targetType == targetType
                && this.passRule == passRule
                && Objects.equals(this.passCount, passCount)
                && hasSameDecimal(this.minScore, minScore);
    }

    // 운영 중 단계 상태만 전환한다.
    public void changeStatus(StageStatus status) {
        this.status = status;
    }

    public boolean isOpenAt(LocalDateTime now) {
        return status == StageStatus.OPEN
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null || now.isBefore(endsAt));
    }

    private boolean hasSameDecimal(BigDecimal current, BigDecimal requested) {
        if (current == null || requested == null) {
            return current == requested;
        }
        return current.compareTo(requested) == 0;
    }
}
