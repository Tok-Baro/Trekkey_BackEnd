package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "graduation_policy", uniqueConstraints = @UniqueConstraint(
        name = "uk_graduation_policy_org_code_version", columnNames = {"organization_id", "policy_code", "version_no"}),
        indexes = @Index(name = "idx_graduation_policy_resolution",
                columnList = "organization_id,status,admission_year_from,admission_year_to"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GraduationPolicy extends GraduationPublicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_unit_id")
    private AcademicUnit academicUnit;

    @Column(name = "policy_code", nullable = false, length = 100)
    private String policyCode;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 30)
    private PolicyType policyType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "admission_year_from")
    private Short admissionYearFrom;

    @Column(name = "admission_year_to")
    private Short admissionYearTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_type", length = 30)
    private AdmissionType admissionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "graduation_path", length = 40)
    private GraduationPath graduationPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "major_plan_type", length = 50)
    private MajorPlanType majorPlanType;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "source_set_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sourceSetHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private GraduationPolicy supersedes;
}
