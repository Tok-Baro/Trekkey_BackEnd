package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "graduation_evaluation_policy", uniqueConstraints = @UniqueConstraint(
        name = "uk_evaluation_policy", columnNames = {"evaluation_id", "policy_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GraduationEvaluationPolicy extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private GraduationEvaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private GraduationPolicy policy;

    @Column(name = "policy_code_snapshot", nullable = false, length = 100)
    private String policyCodeSnapshot;

    @Column(name = "version_no_snapshot", nullable = false)
    private int versionNoSnapshot;
}
