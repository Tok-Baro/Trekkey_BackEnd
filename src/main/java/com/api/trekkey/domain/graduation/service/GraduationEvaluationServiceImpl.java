package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;

import com.api.trekkey.domain.graduation.entity.*;
import com.api.trekkey.domain.graduation.exception.GraduationErrorResponseCode;
import com.api.trekkey.domain.graduation.repository.*;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GraduationEvaluationServiceImpl implements GraduationEvaluationService {
    private static final String HANSUNG_CODE = "HANSUNG_UNIVERSITY";
    private static final String DISCLAIMER = "자가점검 결과이며 한성대학교의 공식 졸업사정을 대체하지 않습니다.";
    private static final String EVALUATOR_VERSION = "hansung-v1";

    private final StudentAcademicProfileRepository profileRepository;
    private final StudentAcademicUnitRepository academicUnitRepository;
    private final StudentCourseRecordRepository courseRepository;
    private final StudentNonCourseRecordRepository nonCourseRepository;
    private final GraduationPolicyRepository policyRepository;
    private final GraduationRequirementRepository requirementRepository;
    private final GraduationEvaluationRepository evaluationRepository;
    private final GraduationEvaluationPolicyRepository evaluationPolicyRepository;
    private final GraduationEvaluationItemRepository evaluationItemRepository;
    private final ObjectMapper objectMapper;

    @Override
    public GraduationEvaluationRes evaluate(Long userId, LocalDate requestedAsOf) {
        LocalDate asOf = requestedAsOf == null ? LocalDate.now() : requestedAsOf;
        if (asOf.isAfter(LocalDate.now())) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_INVALID_POLICY_DATE);
        }
        StudentAcademicProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(
                        GraduationErrorResponseCode.GRADUATION_PROFILE_NOT_CONFIGURED));
        if (!HANSUNG_CODE.equals(profile.getUser().getOrganization().getCode())) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_UNSUPPORTED_ORGANIZATION);
        }

        List<StudentAcademicUnit> units = academicUnitRepository.findAllByProfileIdOrderBySequenceNo(profile.getId());
        List<StudentCourseRecord> courses = courseRepository.findAllByProfileUserIdOrderByTermAscCourseNameAsc(userId);
        List<StudentNonCourseRecord> nonCourses = nonCourseRepository.findAllByProfileUserIdOrderById(userId);
        List<GraduationPolicy> policies = resolvePolicies(profile, units, asOf);

        LinkedHashMap<Long, Outcome> outcomes = new LinkedHashMap<>();
        List<GraduationRequirement> roots = new ArrayList<>();
        for (GraduationPolicy policy : policies) {
            List<GraduationRequirement> requirements = requirementRepository.findAllByPolicyIdOrderBySequenceNo(policy.getId());
            if (requirements.isEmpty()) {
                throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
            }
            Map<Long, List<GraduationRequirement>> children = requirements.stream()
                    .filter(requirement -> requirement.getParent() != null)
                    .collect(Collectors.groupingBy(
                            requirement -> requirement.getParent().getId(), LinkedHashMap::new, Collectors.toList()));
            requirements.stream().filter(requirement -> requirement.getParent() == null).forEach(root -> {
                roots.add(root);
                evaluateNode(root, children, profile, units, courses, nonCourses, outcomes, new HashSet<>());
            });
        }

        List<Outcome> decisive = roots.stream()
                .filter(GraduationRequirement::isRequired)
                .map(root -> outcomes.get(root.getId()))
                .filter(Objects::nonNull)
                .toList();
        int satisfied = (int) decisive.stream().filter(outcome -> outcome.status == RequirementStatus.SATISFIED).count();
        int unsatisfied = (int) decisive.stream().filter(outcome -> outcome.status == RequirementStatus.UNSATISFIED).count();
        int unknown = (int) decisive.stream().filter(outcome -> outcome.status == RequirementStatus.UNKNOWN).count();
        EvaluationStatus status = unsatisfied > 0
                ? EvaluationStatus.NOT_ELIGIBLE
                : unknown > 0 || decisive.isEmpty() ? EvaluationStatus.INDETERMINATE : EvaluationStatus.ELIGIBLE;

        Long currentVersion = profileRepository.findVersionById(profile.getId()).orElse(null);
        if (!Objects.equals(profile.getVersion(), currentVersion)) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_EVALUATION_INPUT_CHANGED);
        }

        LocalDateTime evaluatedAt = LocalDateTime.now();
        String snapshot = inputSnapshot(profile, units, courses, nonCourses);
        GraduationEvaluation evaluation = evaluationRepository.saveAndFlush(GraduationEvaluation.builder()
                .profile(profile)
                .status(status)
                .policyAsOf(asOf)
                .inputHash(sha256(snapshot))
                .inputSnapshotJson(snapshot)
                .profileVersion(profile.getVersion())
                .evaluatorVersion(EVALUATOR_VERSION)
                .satisfiedCount(satisfied)
                .unsatisfiedCount(unsatisfied)
                .unknownCount(unknown)
                .evaluatedAt(evaluatedAt)
                .build());

        policies.forEach(policy -> evaluationPolicyRepository.save(GraduationEvaluationPolicy.builder()
                .evaluation(evaluation)
                .policy(policy)
                .policyCodeSnapshot(policy.getPolicyCode())
                .versionNoSnapshot(policy.getVersionNo())
                .build()));
        outcomes.values().forEach(outcome -> evaluationItemRepository.save(toItem(evaluation, outcome)));

        return toResponse(evaluation, policies, outcomes.values(), satisfied, unsatisfied, unknown);
    }

    private List<GraduationPolicy> resolvePolicies(
            StudentAcademicProfile profile, List<StudentAcademicUnit> units, LocalDate asOf) {
        Set<Long> selectedUnitIds = units.stream().map(unit -> unit.getAcademicUnit().getId()).collect(Collectors.toSet());
        List<GraduationPolicy> policies = policyRepository
                .findAllByOrganizationIdAndStatus(profile.getUser().getOrganization().getId(), PolicyStatus.PUBLISHED)
                .stream()
                .filter(policy -> policy.getAdmissionYearFrom() == null || profile.getAdmissionYear() >= policy.getAdmissionYearFrom())
                .filter(policy -> policy.getAdmissionYearTo() == null || profile.getAdmissionYear() <= policy.getAdmissionYearTo())
                .filter(policy -> policy.getAdmissionType() == null || policy.getAdmissionType() == profile.getAdmissionType())
                .filter(policy -> policy.getGraduationPath() == null || policy.getGraduationPath() == profile.getGraduationPath())
                .filter(policy -> policy.getMajorPlanType() == null || policy.getMajorPlanType() == profile.getMajorPlanType())
                .filter(policy -> !policy.getEffectiveFrom().isAfter(asOf))
                .filter(policy -> policy.getEffectiveTo() == null || !policy.getEffectiveTo().isBefore(asOf))
                .filter(policy -> policy.getAcademicUnit() == null || selectedUnitIds.contains(policy.getAcademicUnit().getId()))
                .sorted(Comparator.comparing(GraduationPolicy::getPolicyCode).thenComparingInt(GraduationPolicy::getVersionNo))
                .toList();
        if (policies.isEmpty()) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_POLICY_NOT_FOUND);
        }
        return policies;
    }

    private Outcome evaluateNode(
            GraduationRequirement requirement,
            Map<Long, List<GraduationRequirement>> children,
            StudentAcademicProfile profile,
            List<StudentAcademicUnit> units,
            List<StudentCourseRecord> courses,
            List<StudentNonCourseRecord> nonCourses,
            Map<Long, Outcome> outcomes,
            Set<Long> visiting) {
        if (!visiting.add(requirement.getId())) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        }
        Outcome outcome;
        if (requirement.getNodeType() == RequirementNodeType.GROUP) {
            List<Outcome> childOutcomes = children.getOrDefault(requirement.getId(), List.of()).stream()
                    .map(child -> evaluateNode(child, children, profile, units, courses, nonCourses, outcomes, visiting))
                    .toList();
            outcome = groupOutcome(requirement, childOutcomes);
        } else {
            outcome = ruleOutcome(requirement, profile, units, courses, nonCourses);
        }
        visiting.remove(requirement.getId());
        outcomes.put(requirement.getId(), outcome);
        return outcome;
    }

    private Outcome groupOutcome(GraduationRequirement requirement, List<Outcome> children) {
        if (children.isEmpty() || requirement.getOperatorType() == null) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        }
        long sat = children.stream().filter(child -> child.status == RequirementStatus.SATISFIED).count();
        long unknown = children.stream().filter(child -> child.status == RequirementStatus.UNKNOWN).count();
        RequirementStatus status = switch (requirement.getOperatorType()) {
            case ALL -> sat == children.size() ? RequirementStatus.SATISFIED
                    : unknown > 0 && sat + unknown == children.size() ? RequirementStatus.UNKNOWN
                    : RequirementStatus.UNSATISFIED;
            case ANY -> sat > 0 ? RequirementStatus.SATISFIED
                    : unknown > 0 ? RequirementStatus.UNKNOWN : RequirementStatus.UNSATISFIED;
            case N_OF -> {
                int min = integer(parameters(requirement), "min");
                yield sat >= min ? RequirementStatus.SATISFIED
                        : sat + unknown >= min ? RequirementStatus.UNKNOWN : RequirementStatus.UNSATISFIED;
            }
        };
        return new Outcome(requirement, status, String.valueOf(sat), requirement.getOperatorType().name(), null,
                requirement.getTitle() + " 그룹 판정: " + status, null);
    }

    private Outcome ruleOutcome(
            GraduationRequirement requirement,
            StudentAcademicProfile profile,
            List<StudentAcademicUnit> units,
            List<StudentCourseRecord> courses,
            List<StudentNonCourseRecord> nonCourses) {
        GraduationRuleType type = requirement.getRuleType();
        if (type == null) throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        JsonNode p = parameters(requirement);
        if ((type == GraduationRuleType.TOTAL_CREDITS_MIN || type == GraduationRuleType.HANSUNG_CREDITS_MIN)
                && hasCreditTotalMismatch(profile, courses)) {
            BigDecimal current = type == GraduationRuleType.TOTAL_CREDITS_MIN
                    ? profile.getTotalCredits() : profile.getHansungCredits();
            return outcome(requirement, RequirementStatus.UNKNOWN, format(current), format(decimal(p, "min")), null,
                    "누계학점과 상세 과목 합계가 일치하지 않아 확인이 필요합니다.");
        }
        if (requiresCompleteDetails(type) && (profile.getInputMode() != InputMode.COURSE_DETAIL
                || profile.getRecordCompleteness() != RecordCompleteness.COMPLETE)) {
            return outcome(requirement, RequirementStatus.UNKNOWN, null, null, null, "전체 성적 입력이 완료되지 않아 확인이 필요합니다.");
        }
        return switch (type) {
            case TOTAL_CREDITS_MIN -> minimum(requirement, profile.getTotalCredits(), decimal(p, "min"), "총 이수학점");
            case HANSUNG_CREDITS_MIN -> minimum(requirement, profile.getHansungCredits(), decimal(p, "min"), "본교 취득학점");
            case TRANSFER_RECOGNIZED_CREDITS_MIN -> minimum(requirement, profile.getTransferRecognizedCredits(), decimal(p, "min"), "전적대 인정학점");
            case GPA_MIN -> minimum(requirement, profile.getCumulativeGpa(), decimal(p, "min"), "누적 GPA");
            case REGISTERED_SEMESTERS_MIN -> minimum(requirement, BigDecimal.valueOf(profile.getRegisteredSemesters()), decimal(p, "min"), "등록학기");
            case ACTIVITY_POINTS_MIN -> minimum(requirement, BigDecimal.valueOf(profile.getActivityPoints()), decimal(p, "min"), "비교과점수");
            case CATEGORY_CREDITS_MIN -> categoryCredits(requirement, courses, CourseCategory.valueOf(text(p, "category")), decimal(p, "min"));
            case ACADEMIC_UNIT_CREDITS_MIN -> academicUnitCredits(requirement, p, units, courses);
            case COURSE_ALL -> coursesRequired(requirement, p, courses, true);
            case COURSE_ANY -> coursesRequired(requirement, p, courses, false);
            case DISTRIBUTION_AREAS_MIN -> distributionAreas(requirement, p, courses);
            case GRADUATE_COURSE_CREDITS_MIN -> categoryCredits(requirement, courses, CourseCategory.GRADUATE_COURSE, decimal(p, "min"));
            case NO_FAIL_GRADE -> noFail(requirement, profile);
            case TOPIK_LEVEL_MIN -> topik(requirement, p, nonCourses);
            case EVIDENCE_VERIFIED -> evidence(requirement, p, units, nonCourses);
            case MANUAL_REVIEW -> outcome(requirement, RequirementStatus.UNKNOWN, null, null, null, "학교 담당자의 확인이 필요한 항목입니다.");
        };
    }

    private Outcome categoryCredits(GraduationRequirement r, List<StudentCourseRecord> courses, CourseCategory category, BigDecimal min) {
        BigDecimal current = completed(courses).filter(course -> course.getCategory() == category)
                .map(StudentCourseRecord::getCredits).reduce(BigDecimal.ZERO, BigDecimal::add);
        return minimum(r, current, min, category.name());
    }

    private Outcome academicUnitCredits(GraduationRequirement r, JsonNode p, List<StudentAcademicUnit> units, List<StudentCourseRecord> courses) {
        Set<Long> unitIds;
        if (p.hasNonNull("academicUnitPublicId")) {
            String publicId = text(p, "academicUnitPublicId");
            unitIds = units.stream().map(StudentAcademicUnit::getAcademicUnit)
                    .filter(unit -> publicId.equals(unit.getPublicId())).map(AcademicUnit::getId).collect(Collectors.toSet());
        } else {
            AcademicUnitRoleType role = AcademicUnitRoleType.valueOf(text(p, "roleType"));
            unitIds = units.stream().filter(unit -> unit.getRoleType() == role)
                    .map(unit -> unit.getAcademicUnit().getId()).collect(Collectors.toSet());
        }
        if (unitIds.isEmpty()) return outcome(r, RequirementStatus.UNKNOWN, null, format(decimal(p, "min")), null, "선택한 전공·트랙 정보를 찾을 수 없습니다.");
        BigDecimal current = completed(courses).filter(course -> course.getAcademicUnit() != null && unitIds.contains(course.getAcademicUnit().getId()))
                .map(StudentCourseRecord::getCredits).reduce(BigDecimal.ZERO, BigDecimal::add);
        return minimum(r, current, decimal(p, "min"), "전공·트랙 학점");
    }

    private Outcome coursesRequired(GraduationRequirement r, JsonNode p, List<StudentCourseRecord> courses, boolean all) {
        Set<String> completed = completed(courses).map(StudentCourseRecord::getCourseCode).filter(Objects::nonNull).collect(Collectors.toSet());
        List<String> required = strings(p, "courseCodes");
        long matched = required.stream().filter(completed::contains).count();
        boolean satisfied = all ? matched == required.size() : matched > 0;
        return outcome(r, satisfied ? RequirementStatus.SATISFIED : RequirementStatus.UNSATISFIED,
                String.valueOf(matched), all ? String.valueOf(required.size()) : "1", satisfied ? "0" : "1", satisfied ? "필수과목 조건을 충족했습니다." : "필수과목 조건을 충족하지 못했습니다.");
    }

    private Outcome distributionAreas(GraduationRequirement r, JsonNode p, List<StudentCourseRecord> courses) {
        long current = completed(courses).filter(course -> course.getCategory() == CourseCategory.GENERAL_DISTRIBUTION)
                .map(StudentCourseRecord::getAcademicCourse).filter(Objects::nonNull)
                .map(AcademicCourse::getDistributionArea).filter(area -> area != null && !area.isBlank()).distinct().count();
        return minimum(r, BigDecimal.valueOf(current), decimal(p, "min"), "배분이수 영역");
    }

    private Outcome noFail(GraduationRequirement r, StudentAcademicProfile profile) {
        return switch (profile.getFailHistoryStatus()) {
            case NONE -> outcome(r, RequirementStatus.SATISFIED, "0", "0", "0", "F 성적 이력이 없습니다.");
            case EXISTS -> outcome(r, RequirementStatus.UNSATISFIED, "1+", "0", "1+", "F 성적 이력이 있습니다.");
            case UNKNOWN -> outcome(r, RequirementStatus.UNKNOWN, null, "0", null, "F 성적 이력 확인이 필요합니다.");
        };
    }

    private Outcome topik(GraduationRequirement r, JsonNode p, List<StudentNonCourseRecord> records) {
        BigDecimal min = decimal(p, "min");
        List<StudentNonCourseRecord> topik = records.stream().filter(record -> record.getRecordType() == NonCourseRecordType.TOPIK).toList();
        Optional<BigDecimal> verified = topik.stream().filter(record -> isVerified(record, p))
                .map(StudentNonCourseRecord::getNumericValue).filter(Objects::nonNull).max(BigDecimal::compareTo);
        if (verified.isPresent()) return minimum(r, verified.get(), min, "TOPIK 급수");
        return outcome(r, RequirementStatus.UNKNOWN, topik.stream().map(StudentNonCourseRecord::getNumericValue)
                .filter(Objects::nonNull).max(BigDecimal::compareTo).map(this::format).orElse(null), format(min), null, "검증된 TOPIK 성적이 필요합니다.");
    }

    private Outcome evidence(GraduationRequirement r, JsonNode p, List<StudentAcademicUnit> units, List<StudentNonCourseRecord> records) {
        if (p.hasNonNull("roleType")) {
            AcademicUnitRoleType role = AcademicUnitRoleType.valueOf(text(p, "roleType"));
            List<StudentAcademicUnit> matched = units.stream().filter(unit -> unit.getRoleType() == role).toList();
            if (matched.stream().anyMatch(unit -> unit.getGraduationEvidenceStatus() == EvidenceStatus.UNIVERSITY_VERIFIED))
                return outcome(r, RequirementStatus.SATISFIED, "VERIFIED", "VERIFIED", "0", "학교 확인이 완료됐습니다.");
            if (matched.stream().anyMatch(unit -> unit.getGraduationEvidenceStatus() == EvidenceStatus.REJECTED))
                return outcome(r, RequirementStatus.UNSATISFIED, "REJECTED", "VERIFIED", "1", "학교 확인에서 미충족 처리됐습니다.");
            return outcome(r, RequirementStatus.UNKNOWN, "UNVERIFIED", "VERIFIED", null, "학교 확인이 필요합니다.");
        }
        NonCourseRecordType recordType = NonCourseRecordType.valueOf(text(p, "recordType"));
        if (p.has("issuerCodes") && !p.get("issuerCodes").isArray()) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        }
        List<StudentNonCourseRecord> matched = records.stream()
                .filter(record -> record.getRecordType() == recordType)
                .filter(record -> !p.hasNonNull("evidenceType")
                        || p.get("evidenceType").asText().equals(record.getExternalEvidenceType()))
                .filter(record -> !p.has("issuerCodes")
                        || containsText(p.get("issuerCodes"), record.getExternalIssuerCode()))
                .toList();
        if (matched.stream().anyMatch(record -> isVerified(record, p)))
            return outcome(r, RequirementStatus.SATISFIED, "VERIFIED", "VERIFIED", "0", "증빙 확인이 완료됐습니다.");
        if (matched.stream().anyMatch(record -> record.getVerificationStatus() == VerificationStatus.REJECTED))
            return outcome(r, RequirementStatus.UNSATISFIED, "REJECTED", "VERIFIED", "1", "증빙이 반려됐습니다.");
        return outcome(r, RequirementStatus.UNKNOWN, "UNVERIFIED", "VERIFIED", null, "학교 확인이 필요합니다.");
    }

    private Outcome minimum(GraduationRequirement r, BigDecimal current, BigDecimal min, String label) {
        boolean satisfied = current.compareTo(min) >= 0;
        BigDecimal remaining = satisfied ? BigDecimal.ZERO : min.subtract(current);
        return outcome(r, satisfied ? RequirementStatus.SATISFIED : RequirementStatus.UNSATISFIED,
                format(current), format(min), format(remaining), satisfied ? label + " 요건을 충족했습니다." : label + "이(가) " + format(remaining) + " 부족합니다.");
    }

    private Outcome outcome(GraduationRequirement r, RequirementStatus status, String current, String required, String remaining, String message) {
        GraduationEvaluationRes.Source source = r.getSource() == null ? null : new GraduationEvaluationRes.Source(
                r.getSource().getTitle(), r.getSource().getOfficialUrl(), r.getSource().getSourceLocator());
        return new Outcome(r, status, current, required, remaining, message, source);
    }

    private boolean requiresCompleteDetails(GraduationRuleType type) {
        return switch (type) {
            case CATEGORY_CREDITS_MIN, ACADEMIC_UNIT_CREDITS_MIN, COURSE_ALL, COURSE_ANY,
                    DISTRIBUTION_AREAS_MIN, GRADUATE_COURSE_CREDITS_MIN -> true;
            default -> false;
        };
    }

    private boolean hasCreditTotalMismatch(StudentAcademicProfile profile, List<StudentCourseRecord> courses) {
        if (profile.getInputMode() != InputMode.COURSE_DETAIL
                || profile.getRecordCompleteness() != RecordCompleteness.COMPLETE) {
            return false;
        }
        BigDecimal completedCredits = completed(courses)
                .map(StudentCourseRecord::getCredits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (profile.getAdmissionType() == AdmissionType.FRESHMAN) {
            return completedCredits.compareTo(profile.getTotalCredits()) != 0;
        }
        return completedCredits.compareTo(profile.getHansungCredits()) != 0
                || profile.getHansungCredits().add(profile.getTransferRecognizedCredits())
                        .compareTo(profile.getTotalCredits()) != 0;
    }

    private java.util.stream.Stream<StudentCourseRecord> completed(List<StudentCourseRecord> courses) {
        return courses.stream().filter(course -> course.getCompletionStatus() == CourseCompletionStatus.COMPLETED);
    }

    private boolean isVerified(VerificationStatus status) {
        return status == VerificationStatus.DOCUMENT_VERIFIED || status == VerificationStatus.UNIVERSITY_VERIFIED;
    }

    private boolean isVerified(StudentNonCourseRecord record, JsonNode parameters) {
        if (!isVerified(record.getVerificationStatus())) return false;
        int required = assuranceRank(parameters.hasNonNull("minimumAssuranceLevel")
                ? parameters.get("minimumAssuranceLevel").asText() : "L0");
        int achieved;
        if (record.getVerificationStatus() == VerificationStatus.UNIVERSITY_VERIFIED) {
            achieved = 3;
        } else if (record.getVerificationAssuranceLevel() == null) {
            achieved = 1;
        } else {
            achieved = assuranceRank(record.getVerificationAssuranceLevel());
        }
        return achieved >= required;
    }

    private int assuranceRank(String level) {
        return switch (level) {
            case "L0" -> 0;
            case "L1" -> 1;
            case "L2" -> 2;
            case "L3" -> 3;
            case "L4" -> 4;
            default -> throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        };
    }

    private boolean containsText(JsonNode array, String value) {
        if (value == null) return false;
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }

    private JsonNode parameters(GraduationRequirement requirement) {
        try {
            return objectMapper.readTree(requirement.getParametersJson());
        } catch (JsonProcessingException e) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        }
    }

    private String text(JsonNode node, String field) {
        if (!node.hasNonNull(field) || node.get(field).asText().isBlank()) throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        return node.get(field).asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isNumber()) throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        return node.get(field).decimalValue();
    }

    private int integer(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).canConvertToInt()) throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        return node.get(field).intValue();
    }

    private List<String> strings(JsonNode node, String field) {
        if (!node.has(field) || !node.get(field).isArray()) throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        List<String> values = new ArrayList<>();
        node.get(field).forEach(value -> values.add(value.asText()));
        if (values.isEmpty()) throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        return values;
    }

    private String inputSnapshot(StudentAcademicProfile p, List<StudentAcademicUnit> units, List<StudentCourseRecord> courses, List<StudentNonCourseRecord> nonCourses) {
        Map<String, Object> snapshot = new TreeMap<>();
        snapshot.put("profilePublicId", p.getPublicId());
        snapshot.put("profileVersion", p.getVersion());
        snapshot.put("admissionYear", p.getAdmissionYear());
        snapshot.put("admissionType", p.getAdmissionType());
        snapshot.put("graduationPath", p.getGraduationPath());
        snapshot.put("majorPlanType", p.getMajorPlanType());
        snapshot.put("totalCredits", p.getTotalCredits());
        snapshot.put("hansungCredits", p.getHansungCredits());
        snapshot.put("transferRecognizedCredits", p.getTransferRecognizedCredits());
        snapshot.put("gpa", p.getCumulativeGpa());
        snapshot.put("activityPoints", p.getActivityPoints());
        snapshot.put("recordCompleteness", p.getRecordCompleteness());
        snapshot.put("unitPublicIds", units.stream().map(unit -> unit.getAcademicUnit().getPublicId()).sorted().toList());
        snapshot.put("courses", courses.stream().map(course -> Map.of(
                "publicId", course.getPublicId(), "term", course.getTerm(), "name", course.getCourseName(),
                "credits", course.getCredits(), "status", course.getCompletionStatus())).toList());
        snapshot.put("nonCoursePublicIds", nonCourses.stream().map(StudentNonCourseRecord::getPublicId).sorted().toList());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_RULE_INVALID);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private GraduationEvaluationItem toItem(GraduationEvaluation evaluation, Outcome outcome) {
        GraduationRequirement r = outcome.requirement;
        return GraduationEvaluationItem.builder()
                .evaluation(evaluation).requirement(r).requirementCode(r.getRequirementCode()).title(r.getTitle())
                .ruleTypeSnapshot(r.getRuleType()).parametersSnapshotJson(r.getParametersJson()).requiredSnapshot(r.isRequired())
                .status(outcome.status).currentValue(outcome.current).requiredValue(outcome.required)
                .remainingValue(outcome.remaining).message(outcome.message)
                .sourceUrl(outcome.source == null ? null : outcome.source.url())
                .sourceLocator(outcome.source == null ? null : outcome.source.locator()).sequenceNo(r.getSequenceNo()).build();
    }

    private GraduationEvaluationRes toResponse(
            GraduationEvaluation evaluation, List<GraduationPolicy> policies, Collection<Outcome> outcomes,
            int satisfied, int unsatisfied, int unknown) {
        List<GraduationEvaluationRes.AppliedPolicy> appliedPolicies = policies.stream().map(policy ->
                new GraduationEvaluationRes.AppliedPolicy(policy.getPublicId(), policy.getPolicyCode(), policy.getVersionNo(), policy.getTitle())).toList();
        List<GraduationEvaluationRes.RequirementResult> results = outcomes.stream().map(outcome ->
                new GraduationEvaluationRes.RequirementResult(outcome.requirement.getRequirementCode(), outcome.requirement.getTitle(),
                        outcome.status, outcome.current, outcome.required, outcome.remaining, outcome.message, outcome.source)).toList();
        return new GraduationEvaluationRes(evaluation.getPublicId(), evaluation.getStatus(), evaluation.getPolicyAsOf(),
                evaluation.getEvaluatedAt(), new GraduationEvaluationRes.Summary(satisfied, unsatisfied, unknown),
                appliedPolicies, results, DISCLAIMER);
    }

    private record Outcome(
            GraduationRequirement requirement, RequirementStatus status, String current, String required,
            String remaining, String message, GraduationEvaluationRes.Source source) {}
}
