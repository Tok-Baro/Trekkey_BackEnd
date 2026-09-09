package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;

import com.api.trekkey.domain.graduation.entity.GraduationPolicy;
import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.entity.StudentAcademicUnit;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes.Coverage;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes.CoverageGap;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Reports modeled coverage, not certification that a published policy is officially complete. */
final class GraduationCoverage {
    private GraduationCoverage() {}

    static Coverage assess(StudentAcademicProfile profile, List<StudentAcademicUnit> units,
            List<GraduationPolicy> policies) {
        Set<PolicyType> applied = policies.stream().map(GraduationPolicy::getPolicyType)
                .collect(Collectors.toSet());
        EnumSet<PolicyType> expected = EnumSet.of(PolicyType.COMMON, PolicyType.GENERAL_EDUCATION,
                PolicyType.MAJOR_PLAN);
        if (profile.getAdmissionType() != AdmissionType.FRESHMAN) expected.add(PolicyType.TRANSFER);
        if (profile.getGraduationPath() == GraduationPath.EARLY) expected.add(PolicyType.EARLY_GRADUATION);
        List<String> missing = expected.stream().filter(type -> !applied.contains(type)).map(Enum::name).toList();
        List<String> uncoveredUnits = units.stream().filter(unit -> policies.stream().noneMatch(policy ->
                        policy.getPolicyType() == PolicyType.UNIT_GRADUATION && policy.getAcademicUnit() != null
                                && Objects.equals(policy.getAcademicUnit().getId(), unit.getAcademicUnit().getId())))
                .map(unit -> unit.getAcademicUnit().getPublicId()).distinct().sorted().toList();
        List<CoverageGap> gaps = new ArrayList<>();
        // This input does not exist in the current profile model. Never infer enrollment/standing
        // from semesters, credits or the mere presence of a published COMMON policy.
        gaps.add(new CoverageGap("ACADEMIC_STANDING_UNAVAILABLE",
                "검증된 재학·학적 상태를 확인할 수 없어 전체 졸업 충족을 확정하지 않습니다."));
        if (!missing.isEmpty()) gaps.add(new CoverageGap("POLICY_TYPES_MISSING",
                "교양·전공 이수체계 또는 해당 학적 경로의 정책 범위가 일부 누락되었습니다."));
        if (units.isEmpty()) gaps.add(new CoverageGap("ACADEMIC_UNITS_MISSING",
                "선택한 학과·트랙 정보가 없어 단위별 졸업요건을 확인할 수 없습니다."));
        if (!uncoveredUnits.isEmpty()) gaps.add(new CoverageGap("UNIT_POLICIES_MISSING",
                "선택한 학과·트랙 중 적용 가능한 단위 졸업정책이 없는 항목이 있습니다."));
        if (profile.getExpectedGraduationYear() == null || profile.getExpectedGraduationMonth() == null)
            gaps.add(new CoverageGap("EXPECTED_GRADUATION_DATE_MISSING",
                    "졸업 예정 연월이 없어 졸업 시점별 정책의 적용 여부를 모두 확인할 수 없습니다."));
        if (profile.getRecordCompleteness() != RecordCompleteness.COMPLETE)
            gaps.add(new CoverageGap("ACADEMIC_RECORDS_INCOMPLETE", "학업 기록이 완전한 상태로 확인되지 않았습니다."));
        if (profile.isTeachingProgram()) gaps.add(new CoverageGap("TEACHING_PROGRAM_POLICY_UNAVAILABLE",
                "교직 이수 경로의 전체 정책은 아직 이 평가에서 다루지 않습니다."));
        if (profile.getGraduationPath() == GraduationPath.BACHELOR_MASTER_LINKED_7
                || profile.getGraduationPath() == GraduationPath.BACHELOR_MASTER_LINKED_8)
            gaps.add(new CoverageGap("LINKED_DEGREE_POLICY_UNAVAILABLE",
                    "학·석사 연계 경로의 전체 정책은 아직 이 평가에서 다루지 않습니다."));
        return new Coverage(gaps.isEmpty(), applied.stream().map(Enum::name).sorted().toList(),
                missing, uncoveredUnits, List.copyOf(gaps));
    }
}
