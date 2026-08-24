package com.api.trekkey.domain.graduation.web.dto;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.AdmissionType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.FailHistoryStatus;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.GraduationPath;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.InputMode;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.MajorPlanType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.RecordCompleteness;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public record GraduationProfileReq(
        @NotNull @Min(1900) Integer admissionYear,
        @NotNull @Min(1900) Integer curriculumYear,
        @NotNull AdmissionType admissionType,
        @NotNull GraduationPath graduationPath,
        @NotNull MajorPlanType majorPlanType,
        @NotNull @Min(0) @Max(30) Integer registeredSemesters,
        @NotNull @DecimalMin("0.0") BigDecimal totalCredits,
        @NotNull @DecimalMin("0.0") BigDecimal hansungCredits,
        @NotNull @DecimalMin("0.0") BigDecimal transferRecognizedCredits,
        @NotNull @DecimalMin("0.0") BigDecimal cumulativeGpa,
        @NotNull @DecimalMin(value = "0.1") BigDecimal gpaScale,
        @NotNull @Min(0) Integer activityPoints,
        @NotNull Boolean internationalStudent,
        @NotNull Boolean teachingProgram,
        @NotNull InputMode inputMode,
        @NotNull RecordCompleteness recordCompleteness,
        @Pattern(regexp = "^\\d{4}-[12]$", message = "기준 학기는 YYYY-1 또는 YYYY-2 형식이어야 합니다")
        String summaryAsOfTerm,
        @Min(1900) Integer expectedGraduationYear,
        @Min(1) @Max(12) Integer expectedGraduationMonth,
        @Valid List<AcademicUnitSelection> academicUnits,
        @NotNull FailHistoryStatus failHistoryStatus,
        boolean recordCompletenessConfirmed,
        Long version) {

    public record AcademicUnitSelection(
            @NotNull String academicUnitPublicId,
            @NotNull com.api.trekkey.domain.graduation.entity.GraduationTypes.AcademicUnitRoleType roleType,
            @Min(1) int sequenceNo) {
    }
}
