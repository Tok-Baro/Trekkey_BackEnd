package com.api.trekkey.domain.graduation.web.dto;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.AdmissionType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.FailHistoryStatus;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.GraduationPath;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.InputMode;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.MajorPlanType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.RecordCompleteness;
import java.math.BigDecimal;
import java.util.List;

public record GraduationProfileRes(
        boolean configured,
        String profilePublicId,
        OrganizationSummary organization,
        String studentNumber,
        Integer admissionYear,
        Integer curriculumYear,
        AdmissionType admissionType,
        GraduationPath graduationPath,
        MajorPlanType majorPlanType,
        Integer registeredSemesters,
        BigDecimal totalCredits,
        BigDecimal hansungCredits,
        BigDecimal transferRecognizedCredits,
        BigDecimal cumulativeGpa,
        BigDecimal gpaScale,
        Integer activityPoints,
        Boolean internationalStudent,
        Boolean teachingProgram,
        InputMode inputMode,
        RecordCompleteness recordCompleteness,
        String summaryAsOfTerm,
        Integer expectedGraduationYear,
        Integer expectedGraduationMonth,
        List<AcademicUnitSelection> academicUnits,
        FailHistoryStatus failHistoryStatus,
        Long version) {

    public record OrganizationSummary(String publicId, String name) {
    }

    public record AcademicUnitSelection(
            String publicId,
            String name,
            String unitType,
            String roleType,
            int sequenceNo) {
    }
}
