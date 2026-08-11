package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "graduation_requirement", uniqueConstraints = @UniqueConstraint(
        name = "uk_graduation_requirement_policy_code", columnNames = {"policy_id", "requirement_code"}),
        indexes = @Index(name = "idx_graduation_requirement_tree", columnList = "policy_id,parent_id,sequence_no"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GraduationRequirement extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private GraduationPolicy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GraduationRequirement parent;

    @Column(name = "requirement_code", nullable = false, length = 100)
    private String requirementCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private RequirementNodeType nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator_type", length = 20)
    private RequirementOperatorType operatorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", length = 50)
    private GraduationRuleType ruleType;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "JSON")
    private String parametersJson;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false)
    private boolean required;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private GraduationPolicySource source;
}
