package com.api.trekkey.domain.review.entity;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.submission.entity.Submission;
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

/**
 * 라운드 참가 및 공식 판정 원장 (erd-mvp §5).
 * REVIEW가 심사위원별 원점수라면, 이 엔티티는 학교가 확정한 공식 점수·순위·통과/탈락이다.
 * FINALIZED 이후에는 수정·삭제할 수 없다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_entry_stage_submission",
                columnNames = {"contest_stage_id", "submission_id"}))
public class ContestStageEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 라운드 참가 및 공식 판정 PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_stage_id", nullable = false)
    // 평가 라운드
    private ContestStage contestStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    // 대상 제출물
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    // 라운드 내 상태
    private EntryStatus status;

    @Column(name = "final_score", precision = 10, scale = 2)
    // 확정 합산 점수 (심사 평균)
    private BigDecimal finalScore;

    @Column(name = "rank_no")
    // 라운드 확정 순위
    private Integer rankNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", length = 10)
    // 판정 방식
    private DecisionType decisionType;

    @Column(name = "decided_by_user_id")
    // 수동 판정 관리자 (값 저장 — 감사 로그와 동일 원칙)
    private Long decidedByUserId;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    // 수동 판정 및 정정 사유
    private String decisionReason;

    @Column(name = "finalized_at")
    // 판정 확정 시각 — 이후 변경 잠금
    private LocalDateTime finalizedAt;

    // 규칙 기반 공식 판정 확정 (라운드 마감 시)
    public void finalizeByRule(BigDecimal finalScore, int rankNo, EntryStatus status, LocalDateTime now) {
        this.finalScore = finalScore;
        this.rankNo = rankNo;
        this.status = status;
        this.decisionType = DecisionType.RULE;
        this.finalizedAt = now;
    }

    // 관리자 수동 판정 (동점 처리·정정)
    public void decideManually(EntryStatus status, Long adminUserId, String reason, LocalDateTime now) {
        this.status = status;
        this.decisionType = DecisionType.MANUAL;
        this.decidedByUserId = adminUserId;
        this.decisionReason = reason;
        this.finalizedAt = now;
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }
}
