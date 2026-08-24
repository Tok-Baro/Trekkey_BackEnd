package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.EvaluationStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "graduation_evaluation", indexes = @Index(
        name = "idx_graduation_evaluation_profile_time", columnList = "profile_id,evaluated_at"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GraduationEvaluation extends GraduationPublicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private StudentAcademicProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvaluationStatus status;

    @Column(name = "policy_as_of", nullable = false)
    private LocalDate policyAsOf;

    @Column(name = "input_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String inputHash;

    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "JSON")
    private String inputSnapshotJson;

    @Column(name = "profile_version", nullable = false)
    private long profileVersion;

    @Column(name = "evaluator_version", nullable = false, length = 30)
    private String evaluatorVersion;

    @Column(name = "satisfied_count", nullable = false)
    private int satisfiedCount;

    @Column(name = "unsatisfied_count", nullable = false)
    private int unsatisfiedCount;

    @Column(name = "unknown_count", nullable = false)
    private int unknownCount;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;
}
