package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.CourseCategory;
import com.api.trekkey.domain.organization.entity.Organization;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "academic_course", uniqueConstraints = @UniqueConstraint(
        name = "uk_academic_course_org_year_code", columnNames = {"organization_id", "academic_year", "course_code"}),
        indexes = @Index(name = "idx_academic_course_org_name", columnList = "organization_id,name"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AcademicCourse extends GraduationPublicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "academic_year", nullable = false)
    private short academicYear;

    @Column(name = "course_code", nullable = false, length = 30)
    private String courseCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal credits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CourseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_unit_id")
    private AcademicUnit academicUnit;

    @Column(name = "distribution_area", length = 40)
    private String distributionArea;

    @Column(name = "valid_from_term", nullable = false, length = 6)
    private String validFromTerm;

    @Column(name = "valid_to_term", length = 6)
    private String validToTerm;
}
