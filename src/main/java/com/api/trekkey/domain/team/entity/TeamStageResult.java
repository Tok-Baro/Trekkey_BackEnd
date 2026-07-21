package com.api.trekkey.domain.team.entity;

import com.api.trekkey.domain.contest.entity.ContestStage;
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
                name = "uk_team_stage_result_team_stage",
                columnNames = {"team_id", "contest_stage_id"}))
public class TeamStageResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 단계별 팀 결과 PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    // 결과 대상 팀
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_stage_id", nullable = false)
    // 결과가 확정된 대회 단계
    private ContestStage contestStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    // 단계 결과 상태: 대기, 통과, 탈락
    private TeamStageResultStatus status;

    @Column(precision = 10, scale = 2)
    // 해당 단계에서 확정된 총점
    private BigDecimal totalScore;

    // 해당 단계에서 확정된 순위
    private Integer rankNo;

    // 통과, 탈락, 순위가 확정된 시각
    private LocalDateTime decidedAt;
}
