package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.graduation.entity.AcademicUnit;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.AcademicUnitStatus;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.AcademicUnitType;
import com.api.trekkey.domain.graduation.repository.AcademicUnitRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class HansungAcademicUnitInitializer implements ApplicationRunner {
    private static final String HANSUNG_CODE = "HANSUNG_UNIVERSITY";
    private final OrganizationRepository organizationRepository;
    private final AcademicUnitRepository academicUnitRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        organizationRepository.findByCode(HANSUNG_CODE).ifPresent(this::initialize);
    }

    private void initialize(Organization organization) {
        AcademicUnit cse = ensure(organization, null, "CSE", "컴퓨터공학부", AcademicUnitType.DIVISION);
        ensure(organization, null, "AI_APPLICATION", "AI응용학과", AcademicUnitType.DEPARTMENT);
        ensure(organization, cse, "CSE_MOBILE", "모바일소프트웨어트랙", AcademicUnitType.TRACK);
        ensure(organization, cse, "CSE_BIGDATA", "빅데이터트랙", AcademicUnitType.TRACK);
        ensure(organization, cse, "CSE_WEB", "웹공학트랙", AcademicUnitType.TRACK);
        ensure(organization, cse, "CSE_CONTENTS", "디지털콘텐츠·가상현실트랙", AcademicUnitType.TRACK);
    }

    private AcademicUnit ensure(
            Organization organization,
            AcademicUnit parent,
            String externalCode,
            String name,
            AcademicUnitType type) {
        return academicUnitRepository.findByOrganizationIdAndExternalCode(organization.getId(), externalCode)
                .orElseGet(() -> academicUnitRepository.save(AcademicUnit.builder()
                        .organization(organization)
                        .parent(parent)
                        .externalCode(externalCode)
                        .name(name)
                        .unitType(type)
                        .validFromYear((short) 2020)
                        .status(AcademicUnitStatus.ACTIVE)
                        .build()));
    }
}
