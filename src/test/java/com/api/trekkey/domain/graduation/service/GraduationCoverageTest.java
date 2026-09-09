package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.graduation.entity.*;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes.CoverageGap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GraduationCoverageTest {
    private StudentAcademicProfile profile() {
        return StudentAcademicProfile.builder().admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR).recordCompleteness(RecordCompleteness.COMPLETE)
                .expectedGraduationYear((short) 2027).expectedGraduationMonth((short) 2).build();
    }

    private GraduationPolicy policy(PolicyType type, AcademicUnit unit) {
        return GraduationPolicy.builder().policyType(type).academicUnit(unit).build();
    }

    @Test
    void commonOnlyDoesNotCoverGeneralEducationMajorPlanOrAcademicStanding() {
        var result = GraduationCoverage.assess(profile(), List.of(), List.of(policy(PolicyType.COMMON, null)));
        assertThat(result.complete()).isFalse();
        assertThat(result.missingPolicyTypes()).containsExactly("GENERAL_EDUCATION", "MAJOR_PLAN");
        assertThat(result.gaps()).extracting(CoverageGap::code)
                .contains("ACADEMIC_STANDING_UNAVAILABLE", "ACADEMIC_UNITS_MISSING");
    }

    @Test
    void matchingAllModeledPolicyTypesStillCannotInventVerifiedAcademicStanding() {
        AcademicUnit unit = AcademicUnit.builder().build();
        ReflectionTestUtils.setField(unit, "id", 1L);
        ReflectionTestUtils.setField(unit, "publicId", "synthetic-unit");
        var selected = StudentAcademicUnit.builder().academicUnit(unit).roleType(AcademicUnitRoleType.PRIMARY).build();
        var result = GraduationCoverage.assess(profile(), List.of(selected), List.of(
                policy(PolicyType.COMMON, null), policy(PolicyType.GENERAL_EDUCATION, null),
                policy(PolicyType.MAJOR_PLAN, null), policy(PolicyType.UNIT_GRADUATION, unit)));
        assertThat(result.complete()).isFalse();
        assertThat(result.missingPolicyTypes()).isEmpty();
        assertThat(result.uncoveredUnitPublicIds()).isEmpty();
        assertThat(result.gaps()).extracting(CoverageGap::code).containsExactly("ACADEMIC_STANDING_UNAVAILABLE");
    }

    @Test
    void unrelatedUnitPolicyCannotCoverSelectedUnit() {
        AcademicUnit selected = AcademicUnit.builder().build(), other = AcademicUnit.builder().build();
        ReflectionTestUtils.setField(selected, "id", 1L);
        ReflectionTestUtils.setField(selected, "publicId", "selected");
        ReflectionTestUtils.setField(other, "id", 2L);
        var result = GraduationCoverage.assess(profile(),
                List.of(StudentAcademicUnit.builder().academicUnit(selected).build()),
                List.of(policy(PolicyType.UNIT_GRADUATION, other)));
        assertThat(result.uncoveredUnitPublicIds()).containsExactly("selected");
    }

    @Test
    void transferAndEarlyGraduationHaveExplicitMissingPolicyTypes() {
        var p = profile();
        ReflectionTestUtils.setField(p, "admissionType", AdmissionType.GENERAL_TRANSFER);
        ReflectionTestUtils.setField(p, "graduationPath", GraduationPath.EARLY);
        var result = GraduationCoverage.assess(p, List.of(), List.of());
        assertThat(result.missingPolicyTypes()).contains("TRANSFER", "EARLY_GRADUATION");
    }

    @Test
    void missingExpectedDateAndPartialRecordsAreNotSilentlyIgnored() {
        var p = profile();
        ReflectionTestUtils.setField(p, "expectedGraduationYear", null);
        ReflectionTestUtils.setField(p, "recordCompleteness", RecordCompleteness.PARTIAL);
        var result = GraduationCoverage.assess(p, List.of(), List.of());
        assertThat(result.gaps()).extracting(CoverageGap::code)
                .contains("EXPECTED_GRADUATION_DATE_MISSING", "ACADEMIC_RECORDS_INCOMPLETE");
    }

    @Test
    void unsupportedTeachingAndLinkedDegreeAreVisibleGaps() {
        var p = profile();
        ReflectionTestUtils.setField(p, "teachingProgram", true);
        ReflectionTestUtils.setField(p, "graduationPath", GraduationPath.BACHELOR_MASTER_LINKED_7);
        var result = GraduationCoverage.assess(p, List.of(), List.of());
        assertThat(result.gaps()).extracting(CoverageGap::code)
                .contains("TEACHING_PROGRAM_POLICY_UNAVAILABLE", "LINKED_DEGREE_POLICY_UNAVAILABLE");
    }
}
