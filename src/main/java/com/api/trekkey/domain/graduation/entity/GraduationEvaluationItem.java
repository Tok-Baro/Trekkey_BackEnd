package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.GraduationRuleType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.RequirementStatus;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "graduation_evaluation_item", uniqueConstraints = @UniqueConstraint(
        name = "uk_evaluation_requirement_code", columnNames = {"evaluation_id", "requirement_code"}),
        indexes = @Index(name = "idx_evaluation_item_status_sequence", columnList = "evaluation_id,status,sequence_no"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GraduationEvaluationItem extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private GraduationEvaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private GraduationRequirement requirement;

    @Column(name = "requirement_code", nullable = false, length = 100)
    private String requirementCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type_snapshot", length = 50)
    private GraduationRuleType ruleTypeSnapshot;

    @Column(name = "parameters_snapshot_json", nullable = false, columnDefinition = "JSON")
    private String parametersSnapshotJson;

    @Column(name = "required_snapshot", nullable = false)
    private boolean requiredSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequirementStatus status;

    @Column(name = "current_value", length = 100)
    private String currentValue;

    @Column(name = "required_value", length = 100)
    private String requiredValue;

    @Column(name = "remaining_value", length = 100)
    private String remainingValue;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_locator", length = 300)
    private String sourceLocator;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;
}
