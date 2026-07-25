package com.api.trekkey.domain.review.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 심사위원이 제출한 평가 결과 1건 — 배정당 한 건이며 제출 후 수정할 수 없다 (erd-mvp §5).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 심사 결과 PK
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false, unique = true)
    // 대상 심사 배정 — 배정당 한 건
    private ReviewAssignment assignment;

    @Column(name = "total_score", nullable = false, precision = 10, scale = 2)
    // 항목별 점수 합계
    private BigDecimal totalScore;

    @Column(columnDefinition = "TEXT")
    // 심사 의견
    private String comment;

    @Column(name = "submitted_at", nullable = false)
    // 심사 제출 시각
    private LocalDateTime submittedAt;
}
