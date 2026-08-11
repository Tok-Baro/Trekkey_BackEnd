package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.graduation.entity.*;
import com.api.trekkey.domain.graduation.repository.*;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GraduationEvaluationServiceImplTest {
    @Mock private StudentAcademicProfileRepository profileRepository;
    @Mock private StudentAcademicUnitRepository academicUnitRepository;
    @Mock private StudentCourseRecordRepository courseRepository;
    @Mock private StudentNonCourseRecordRepository nonCourseRepository;
    @Mock private GraduationPolicyRepository policyRepository;
    @Mock private GraduationRequirementRepository requirementRepository;
    @Mock private GraduationEvaluationRepository evaluationRepository;
    @Mock private GraduationEvaluationPolicyRepository evaluationPolicyRepository;
    @Mock private GraduationEvaluationItemRepository evaluationItemRepository;

    private GraduationEvaluationServiceImpl service;
    private StudentAcademicProfile profile;
    private List<GraduationRequirement> requirements;

    @BeforeEach
    void setUp() {
        service = new GraduationEvaluationServiceImpl(
                profileRepository, academicUnitRepository, courseRepository, nonCourseRepository,
                policyRepository, requirementRepository, evaluationRepository,
                evaluationPolicyRepository, evaluationItemRepository, new ObjectMapper());
        Fixture fixture = fixture();
        profile = fixture.profile;
        requirements = fixture.requirements;
        given(profileRepository.findByUserId(10L)).willReturn(Optional.of(profile));
        given(profileRepository.findVersionById(100L)).willReturn(Optional.of(0L));
        given(academicUnitRepository.findAllByProfileIdOrderBySequenceNo(100L)).willReturn(fixture.units);
        given(courseRepository.findAllByProfileUserIdOrderByTermAscCourseNameAsc(10L)).willReturn(fixture.courses);
        given(nonCourseRepository.findAllByProfileUserIdOrderById(10L)).willReturn(fixture.nonCourses);
        given(policyRepository.findAllByOrganizationIdAndStatus(1L, PolicyStatus.PUBLISHED)).willReturn(List.of(fixture.policy));
        given(requirementRepository.findAllByPolicyIdOrderBySequenceNo(200L)).willReturn(requirements);
        org.mockito.Mockito.lenient().when(evaluationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            GraduationEvaluation evaluation = invocation.getArgument(0);
            ReflectionTestUtils.setField(evaluation, "id", 300L);
            ReflectionTestUtils.setField(evaluation, "publicId", "evaluation-public-id");
            return evaluation;
        });
    }

    @Test
    void evaluatesEveryRuleTypeAndPersistsSnapshotItems() {
        GraduationEvaluationRes result = service.evaluate(10L, LocalDate.of(2026, 8, 11));

        assertThat(result.status()).isEqualTo(EvaluationStatus.ELIGIBLE);
        assertThat(result.summary()).isEqualTo(new GraduationEvaluationRes.Summary(15, 0, 0));
        assertThat(result.requirements()).hasSize(16);
        assertThat(result.requirements()).extracting(GraduationEvaluationRes.RequirementResult::code)
                .containsExactlyInAnyOrder(requirements.stream().map(GraduationRequirement::getRequirementCode).toArray(String[]::new));
        assertThat(result.requirements()).filteredOn(item -> item.code().equals("MANUAL_REVIEW"))
                .extracting(GraduationEvaluationRes.RequirementResult::status).containsExactly(RequirementStatus.UNKNOWN);

        ArgumentCaptor<GraduationEvaluationItem> itemCaptor = ArgumentCaptor.forClass(GraduationEvaluationItem.class);
        verify(evaluationItemRepository, org.mockito.Mockito.times(16)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting(GraduationEvaluationItem::getRuleTypeSnapshot)
                .containsExactlyInAnyOrder(GraduationRuleType.values());
        verify(evaluationPolicyRepository).save(any());
    }

    @Test
    void incompleteCourseInputPropagatesUnknownInsteadOfFalseFailure() {
        ReflectionTestUtils.setField(profile, "recordCompleteness", RecordCompleteness.PARTIAL);

        GraduationEvaluationRes result = service.evaluate(10L, LocalDate.of(2026, 8, 11));

        assertThat(result.status()).isEqualTo(EvaluationStatus.INDETERMINATE);
        assertThat(result.requirements()).filteredOn(item -> Set.of(
                        "CATEGORY_CREDITS_MIN", "ACADEMIC_UNIT_CREDITS_MIN", "COURSE_ALL", "COURSE_ANY",
                        "DISTRIBUTION_AREAS_MIN", "GRADUATE_COURSE_CREDITS_MIN").contains(item.code()))
                .allMatch(item -> item.status() == RequirementStatus.UNKNOWN);
    }

    @Test
    void numericShortageMakesTheOverallResultNotEligible() {
        ReflectionTestUtils.setField(profile, "totalCredits", new BigDecimal("120.0"));
        ReflectionTestUtils.setField(profile, "inputMode", InputMode.SUMMARY_ONLY);

        GraduationEvaluationRes result = service.evaluate(10L, LocalDate.of(2026, 8, 11));

        assertThat(result.status()).isEqualTo(EvaluationStatus.NOT_ELIGIBLE);
        assertThat(result.requirements()).filteredOn(item -> item.code().equals("TOTAL_CREDITS_MIN"))
                .singleElement().satisfies(item -> {
                    assertThat(item.status()).isEqualTo(RequirementStatus.UNSATISFIED);
                    assertThat(item.remainingValue()).isEqualTo("10");
                });
    }

    @Test
    void summaryDetailMismatchBlocksEligibleResult() {
        ReflectionTestUtils.setField(profile, "totalCredits", new BigDecimal("131.0"));

        GraduationEvaluationRes result = service.evaluate(10L, LocalDate.of(2026, 8, 11));

        assertThat(result.status()).isEqualTo(EvaluationStatus.INDETERMINATE);
        assertThat(result.requirements()).filteredOn(item -> item.code().equals("TOTAL_CREDITS_MIN"))
                .extracting(GraduationEvaluationRes.RequirementResult::status).containsExactly(RequirementStatus.UNKNOWN);
    }

    @Test
    void evaluatesAllAnyAndNOfGroups() {
        List<GraduationRequirement> grouped = groupRequirements(requirements.get(0).getPolicy());
        given(requirementRepository.findAllByPolicyIdOrderBySequenceNo(200L)).willReturn(grouped);

        GraduationEvaluationRes result = service.evaluate(10L, LocalDate.of(2026, 8, 11));

        assertThat(result.status()).isEqualTo(EvaluationStatus.ELIGIBLE);
        assertThat(result.summary()).isEqualTo(new GraduationEvaluationRes.Summary(3, 0, 0));
        assertThat(result.requirements()).filteredOn(item -> Set.of("GROUP_ALL", "GROUP_ANY", "GROUP_N_OF").contains(item.code()))
                .allMatch(item -> item.status() == RequirementStatus.SATISFIED);
    }

    @Test
    void inputVersionChangeAbortsTheEvaluation() {
        given(profileRepository.findVersionById(100L)).willReturn(Optional.of(1L));

        assertThatThrownBy(() -> service.evaluate(10L, LocalDate.of(2026, 8, 11)))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode().getCode())
                        .isEqualTo("GRADUATION_EVALUATION_INPUT_CHANGED"));
    }

    private Fixture fixture() {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "id", 1L);
        ReflectionTestUtils.setField(organization, "code", "HANSUNG_UNIVERSITY");
        ReflectionTestUtils.setField(organization, "name", "한성대학교");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        User user = User.builder().organization(organization).name("학생").email("student@test.com")
                .password("encoded").role(UserRole.PARTICIPANT).memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE).studentId("2021001").build();
        ReflectionTestUtils.setField(user, "id", 10L);
        StudentAcademicProfile profile = StudentAcademicProfile.builder().user(user)
                .admissionYear((short) 2021).curriculumYear((short) 2021).admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR).majorPlanType(MajorPlanType.CONVERGENCE_I)
                .registeredSemesters((short) 8).totalCredits(new BigDecimal("130.0"))
                .hansungCredits(new BigDecimal("130.0")).transferRecognizedCredits(BigDecimal.ZERO)
                .cumulativeGpa(new BigDecimal("3.50")).gpaScale(new BigDecimal("4.5")).activityPoints(800)
                .inputMode(InputMode.COURSE_DETAIL).recordCompleteness(RecordCompleteness.COMPLETE)
                .summaryAsOfTerm("2026-1").failHistoryStatus(FailHistoryStatus.NONE).build();
        ReflectionTestUtils.setField(profile, "id", 100L);
        ReflectionTestUtils.setField(profile, "publicId", "profile-public-id");
        ReflectionTestUtils.setField(profile, "version", 0L);

        AcademicUnit primary = AcademicUnit.builder().organization(organization).name("모바일소프트웨어트랙")
                .unitType(AcademicUnitType.TRACK).validFromYear((short) 2017).status(AcademicUnitStatus.ACTIVE).build();
        ReflectionTestUtils.setField(primary, "id", 110L);
        ReflectionTestUtils.setField(primary, "publicId", "primary-unit");
        StudentAcademicUnit selectedUnit = StudentAcademicUnit.builder().profile(profile).academicUnit(primary)
                .roleType(AcademicUnitRoleType.PRIMARY).sequenceNo(1)
                .graduationEvidenceStatus(EvidenceStatus.UNIVERSITY_VERIFIED).verifiedBy(user).build();

        AcademicCourse distributionA = catalog(organization, "DIST-A", "인문", 120L);
        AcademicCourse distributionB = catalog(organization, "DIST-B", "사회", 121L);
        List<StudentCourseRecord> courses = List.of(
                course(profile, null, null, "C1", CourseCategory.GENERAL_REQUIRED, "3.0", 1),
                course(profile, distributionA, null, "C2", CourseCategory.GENERAL_DISTRIBUTION, "3.0", 2),
                course(profile, distributionB, null, "C3", CourseCategory.GENERAL_DISTRIBUTION, "3.0", 3),
                course(profile, null, primary, "MAJOR1", CourseCategory.MAJOR_REQUIRED, "39.0", 4),
                course(profile, null, null, "GRAD1", CourseCategory.GRADUATE_COURSE, "6.0", 5),
                course(profile, null, null, "FREE1", CourseCategory.FREE_ELECTIVE, "76.0", 6));
        StudentNonCourseRecord topik = nonCourse(profile, NonCourseRecordType.TOPIK, new BigDecimal("4"), 130L);

        GraduationPolicy policy = GraduationPolicy.builder().organization(organization).policyCode("HANSUNG-ALL")
                .versionNo(1).policyType(PolicyType.COMMON).title("전체 규칙 테스트")
                .effectiveFrom(LocalDate.of(2020, 1, 1)).status(PolicyStatus.PUBLISHED)
                .sourceSetHash("a".repeat(64)).createdBy(user).build();
        ReflectionTestUtils.setField(policy, "id", 200L);
        ReflectionTestUtils.setField(policy, "publicId", "policy-public-id");
        List<GraduationRequirement> requirements = allRequirements(policy);
        return new Fixture(profile, policy, List.of(selectedUnit), courses, List.of(topik), requirements);
    }

    private AcademicCourse catalog(Organization organization, String code, String area, long id) {
        AcademicCourse course = AcademicCourse.builder().organization(organization).academicYear((short) 2026)
                .courseCode(code).name(code).credits(new BigDecimal("3.0")).category(CourseCategory.GENERAL_DISTRIBUTION)
                .distributionArea(area).validFromTerm("2026-1").build();
        ReflectionTestUtils.setField(course, "id", id);
        ReflectionTestUtils.setField(course, "publicId", "catalog-" + id);
        return course;
    }

    private StudentCourseRecord course(StudentAcademicProfile profile, AcademicCourse catalog, AcademicUnit unit,
            String code, CourseCategory category, String credits, int sequence) {
        StudentCourseRecord record = StudentCourseRecord.builder().profile(profile).academicCourse(catalog).term("2026-1")
                .courseCode(code).courseName(code).credits(new BigDecimal(credits)).grade("A+")
                .completionStatus(CourseCompletionStatus.COMPLETED).category(category).academicUnit(unit)
                .mappingStatus(CourseMappingStatus.UNIVERSITY_VERIFIED).sourceType(RecordSourceType.ADMIN).build();
        ReflectionTestUtils.setField(record, "id", 140L + sequence);
        ReflectionTestUtils.setField(record, "publicId", "course-" + sequence);
        return record;
    }

    private StudentNonCourseRecord nonCourse(StudentAcademicProfile profile, NonCourseRecordType type, BigDecimal value, long id) {
        StudentNonCourseRecord record = StudentNonCourseRecord.builder().profile(profile).recordType(type).title(type.name())
                .numericValue(value).verificationStatus(VerificationStatus.DOCUMENT_VERIFIED).build();
        ReflectionTestUtils.setField(record, "id", id);
        ReflectionTestUtils.setField(record, "publicId", "non-course-" + id);
        return record;
    }

    private List<GraduationRequirement> allRequirements(GraduationPolicy policy) {
        LinkedHashMap<GraduationRuleType, String> parameters = new LinkedHashMap<>();
        parameters.put(GraduationRuleType.TOTAL_CREDITS_MIN, "{\"min\":130}");
        parameters.put(GraduationRuleType.HANSUNG_CREDITS_MIN, "{\"min\":130}");
        parameters.put(GraduationRuleType.TRANSFER_RECOGNIZED_CREDITS_MIN, "{\"min\":0}");
        parameters.put(GraduationRuleType.CATEGORY_CREDITS_MIN, "{\"category\":\"GENERAL_REQUIRED\",\"min\":3}");
        parameters.put(GraduationRuleType.ACADEMIC_UNIT_CREDITS_MIN, "{\"roleType\":\"PRIMARY\",\"min\":39}");
        parameters.put(GraduationRuleType.COURSE_ALL, "{\"courseCodes\":[\"C1\",\"C2\"]}");
        parameters.put(GraduationRuleType.COURSE_ANY, "{\"courseCodes\":[\"C3\",\"C4\"]}");
        parameters.put(GraduationRuleType.DISTRIBUTION_AREAS_MIN, "{\"min\":2}");
        parameters.put(GraduationRuleType.GPA_MIN, "{\"min\":2.0}");
        parameters.put(GraduationRuleType.REGISTERED_SEMESTERS_MIN, "{\"min\":8}");
        parameters.put(GraduationRuleType.NO_FAIL_GRADE, "{}");
        parameters.put(GraduationRuleType.ACTIVITY_POINTS_MIN, "{\"min\":800}");
        parameters.put(GraduationRuleType.GRADUATE_COURSE_CREDITS_MIN, "{\"min\":6}");
        parameters.put(GraduationRuleType.TOPIK_LEVEL_MIN, "{\"min\":4}");
        parameters.put(GraduationRuleType.EVIDENCE_VERIFIED, "{\"roleType\":\"PRIMARY\"}");
        parameters.put(GraduationRuleType.MANUAL_REVIEW, "{}");
        AtomicLong id = new AtomicLong(1000);
        List<GraduationRequirement> requirements = new ArrayList<>();
        parameters.forEach((type, json) -> {
            GraduationRequirement requirement = GraduationRequirement.builder().policy(policy).requirementCode(type.name())
                    .title(type.name()).nodeType(RequirementNodeType.RULE).ruleType(type).parametersJson(json)
                    .sequenceNo(requirements.size() + 1).required(type != GraduationRuleType.MANUAL_REVIEW).build();
            ReflectionTestUtils.setField(requirement, "id", id.getAndIncrement());
            requirements.add(requirement);
        });
        return requirements;
    }

    private List<GraduationRequirement> groupRequirements(GraduationPolicy policy) {
        AtomicLong id = new AtomicLong(3000);
        List<GraduationRequirement> all = new ArrayList<>();
        GraduationRequirement groupAll = group(policy, id.getAndIncrement(), "GROUP_ALL", RequirementOperatorType.ALL, "{}", 1);
        GraduationRequirement groupAny = group(policy, id.getAndIncrement(), "GROUP_ANY", RequirementOperatorType.ANY, "{}", 2);
        GraduationRequirement groupN = group(policy, id.getAndIncrement(), "GROUP_N_OF", RequirementOperatorType.N_OF, "{\"min\":2}", 3);
        all.addAll(List.of(groupAll, groupAny, groupN));
        all.add(rule(policy, groupAll, id.getAndIncrement(), "ALL_TOTAL", GraduationRuleType.TOTAL_CREDITS_MIN, "{\"min\":130}", 1));
        all.add(rule(policy, groupAll, id.getAndIncrement(), "ALL_GPA", GraduationRuleType.GPA_MIN, "{\"min\":2}", 2));
        all.add(rule(policy, groupAny, id.getAndIncrement(), "ANY_TOTAL", GraduationRuleType.TOTAL_CREDITS_MIN, "{\"min\":999}", 1));
        all.add(rule(policy, groupAny, id.getAndIncrement(), "ANY_GPA", GraduationRuleType.GPA_MIN, "{\"min\":2}", 2));
        all.add(rule(policy, groupN, id.getAndIncrement(), "NOF_TOTAL", GraduationRuleType.TOTAL_CREDITS_MIN, "{\"min\":130}", 1));
        all.add(rule(policy, groupN, id.getAndIncrement(), "NOF_GPA", GraduationRuleType.GPA_MIN, "{\"min\":2}", 2));
        all.add(rule(policy, groupN, id.getAndIncrement(), "NOF_POINTS", GraduationRuleType.ACTIVITY_POINTS_MIN, "{\"min\":999}", 3));
        return all;
    }

    private GraduationRequirement group(GraduationPolicy policy, long id, String code,
            RequirementOperatorType operator, String parameters, int sequence) {
        GraduationRequirement group = GraduationRequirement.builder().policy(policy).requirementCode(code).title(code)
                .nodeType(RequirementNodeType.GROUP).operatorType(operator).parametersJson(parameters)
                .sequenceNo(sequence).required(true).build();
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private GraduationRequirement rule(GraduationPolicy policy, GraduationRequirement parent, long id, String code,
            GraduationRuleType type, String parameters, int sequence) {
        GraduationRequirement rule = GraduationRequirement.builder().policy(policy).parent(parent).requirementCode(code).title(code)
                .nodeType(RequirementNodeType.RULE).ruleType(type).parametersJson(parameters)
                .sequenceNo(sequence).required(false).build();
        ReflectionTestUtils.setField(rule, "id", id);
        return rule;
    }

    private record Fixture(
            StudentAcademicProfile profile, GraduationPolicy policy, List<StudentAcademicUnit> units,
            List<StudentCourseRecord> courses, List<StudentNonCourseRecord> nonCourses,
            List<GraduationRequirement> requirements) {}
}
