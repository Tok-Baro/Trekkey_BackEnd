package com.api.trekkey.domain.contest.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
                name = "uk_review_criterion_stage_code",
                columnNames = {"contest_stage_id", "code"}))
public class ReviewCriterion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 평가 기준 PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_stage_id", nullable = false)
    // 평가 기준이 적용되는 심사 단계
    private ContestStage contestStage;

    @Column(nullable = false, length = 60)
    // 내부 기준 코드. 예: creativity
    private String code;

    @Column(nullable = false, length = 60)
    // 화면 표시명. 예: 창의성
    private String label;

    @Column(nullable = false)
    // 최대 점수
    private int maxScore;

    @Column(nullable = false)
    // 평가 화면 표시 순서
    private int sortOrder;

    @Builder.Default
    @Column(nullable = false)
    // 사용 여부
    private boolean active = true;

    public void update(String label, int maxScore, int sortOrder) {
        this.label = label;
        this.maxScore = maxScore;
        this.sortOrder = sortOrder;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean hasSameConfiguration(String code, String label, int maxScore, int sortOrder) {
        return this.code.equals(code)
                && this.label.equals(label)
                && this.maxScore == maxScore
                && this.sortOrder == sortOrder;
    }
}
