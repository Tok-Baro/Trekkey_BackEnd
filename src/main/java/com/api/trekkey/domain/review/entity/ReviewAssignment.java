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
        uniqueConstraints = @UniqueConstraint(
                name = "uk_assignment_judge_entry",
                columnNames = {"contest_judge_id", "contest_stage_entry_id"}))
public class ReviewAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 심사 배정 PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_judge_id", nullable = false)
    // 배정 심사위원
    private ContestJudge contestJudge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_stage_entry_id", nullable = false)
    // 라운드별 심사 대상 (공식 판정 원장 기준 — erd-mvp)
    private ContestStageEntry contestStageEntry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    // 배정 상태
    private AssignmentStatus status;

    @Column(name = "assigned_at", nullable = false)
    // 배정 시각
    private LocalDateTime assignedAt;

    @Column(name = "completed_at")
    // 심사 완료 시각
    private LocalDateTime completedAt;

    // 심사 제출 완료 — 이후 이 배정의 REVIEW는 불변이다
    public void complete(LocalDateTime now) {
        this.status = AssignmentStatus.COMPLETED;
        this.completedAt = now;
    }
}
