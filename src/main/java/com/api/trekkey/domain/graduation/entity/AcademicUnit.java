package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.AcademicUnitStatus;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.AcademicUnitType;
import com.api.trekkey.domain.organization.entity.Organization;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "academic_unit", uniqueConstraints = @UniqueConstraint(
        name = "uk_academic_unit_org_external_code", columnNames = {"organization_id", "external_code"}),
        indexes = @Index(name = "idx_academic_unit_org_status_name", columnList = "organization_id,status,name"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AcademicUnit extends GraduationPublicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AcademicUnit parent;

    @Column(name = "external_code", length = 50)
    private String externalCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 30)
    private AcademicUnitType unitType;

    @Column(name = "valid_from_year", nullable = false)
    private short validFromYear;

    @Column(name = "valid_to_year")
    private Short validToYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcademicUnitStatus status;
}
