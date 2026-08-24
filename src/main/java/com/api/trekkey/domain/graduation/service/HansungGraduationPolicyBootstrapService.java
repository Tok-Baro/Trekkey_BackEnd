package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;

import com.api.trekkey.domain.graduation.entity.AcademicUnit;
import com.api.trekkey.domain.graduation.entity.GraduationPolicy;
import com.api.trekkey.domain.graduation.entity.GraduationPolicySource;
import com.api.trekkey.domain.graduation.entity.GraduationRequirement;
import com.api.trekkey.domain.graduation.repository.AcademicUnitRepository;
import com.api.trekkey.domain.graduation.repository.GraduationPolicyRepository;
import com.api.trekkey.domain.graduation.repository.GraduationPolicySourceRepository;
import com.api.trekkey.domain.graduation.repository.GraduationRequirementRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HansungGraduationPolicyBootstrapService implements ApplicationRunner {
    private static final String HANSUNG_CODE = "HANSUNG_UNIVERSITY";
    public static final String GENERAL_URL = "https://www.hansung.ac.kr/hansung/6234/subview.do";
    public static final String AI_URL = "https://www.hansung.ac.kr/bbs/CreCon/1943/222860/artclView.do";
    public static final String CSE_URL = "https://www.hansung.ac.kr/bbs/CSE/1974/223781/artclView.do";

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AcademicUnitRepository academicUnitRepository;
    private final GraduationPolicyRepository policyRepository;
    private final GraduationPolicySourceRepository sourceRepository;
    private final GraduationRequirementRepository requirementRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        organizationRepository.findByCode(HANSUNG_CODE).ifPresent(organization ->
                userRepository.findFirstByOrganizationIdOrderByIdAsc(organization.getId())
                        .ifPresent(user -> ensure(organization, user)));
    }

    @Transactional
    public void ensure(User actor) {
        if (actor != null && HANSUNG_CODE.equals(actor.getOrganization().getCode())) ensure(actor.getOrganization(), actor);
    }

    private void ensure(Organization organization, User actor) {
        createCommon(organization, actor);
        academicUnitRepository.findByOrganizationIdAndExternalCode(organization.getId(), "AI_APPLICATION")
                .ifPresent(unit -> createAiPolicies(organization, unit, actor));
        academicUnitRepository.findByOrganizationIdAndExternalCode(organization.getId(), "CSE")
                .ifPresent(unit -> createCsePolicy(organization, unit, actor));
    }

    private void createCommon(Organization organization, User actor) {
        String code = "HS_COMMON_2026";
        if (exists(organization, code)) return;
        GraduationPolicy policy = policy(organization, null, code, PolicyType.COMMON,
                "한성대학교 공통 졸업요건", null, null, actor);
        GraduationPolicySource source = source(policy, GENERAL_URL, "한성대학교 졸업 및 학위 안내", LocalDate.of(2026, 1, 23));
        GraduationRequirement root = group(policy, null, "COMMON_ALL", "공통 졸업요건", RequirementOperatorType.ALL, 1, true, source);
        rule(policy, root, "TOTAL_130", "총 이수학점 130학점", GraduationRuleType.TOTAL_CREDITS_MIN, "{\"min\":130}", 1, source);
        rule(policy, root, "GPA_2", "평점평균 2.0 이상", GraduationRuleType.GPA_MIN, "{\"min\":2.0}", 2, source);
        rule(policy, root, "SEMESTERS_8", "등록학기 8학기 이상", GraduationRuleType.REGISTERED_SEMESTERS_MIN, "{\"min\":8}", 3, source);
        rule(policy, root, "ACTIVITY_800", "비교과포인트 800점", GraduationRuleType.ACTIVITY_POINTS_MIN, "{\"min\":800}", 4, source);
        rule(policy, root, "TOPIK_4", "순수외국인 유학생 TOPIK", GraduationRuleType.TOPIK_LEVEL_MIN,
                "{\"min\":4,\"verificationStatuses\":[\"DOCUMENT_VERIFIED\",\"UNIVERSITY_VERIFIED\"]}", 5, source);
    }

    private void createAiPolicies(Organization organization, AcademicUnit unit, User actor) {
        if (!exists(organization, "HS_AI_CERT_2025")) {
            GraduationPolicy policy = policy(organization, unit, "HS_AI_CERT_2025", PolicyType.UNIT_GRADUATION,
                    "AI응용학과 졸업 인증요건", (short) 2025, null, actor);
            GraduationPolicySource source = source(policy, AI_URL, "AI응용학과 졸업요건 인정 신청 안내", LocalDate.of(2026, 6, 10));
            GraduationRequirement root = group(policy, null, "AI_CERT_ANY", "영어·논문·공모전 중 1개", RequirementOperatorType.ANY, 1, true, source);
            rule(policy, root, "AI_ENGLISH", "공인 영어성적", GraduationRuleType.NON_COURSE_VALUE_MIN,
                    "{\"recordType\":\"ENGLISH_SCORE\",\"min\":700,\"verificationStatuses\":[\"DOCUMENT_VERIFIED\",\"UNIVERSITY_VERIFIED\"]}", 1, source);
            rule(policy, root, "AI_THESIS", "학술 논문", GraduationRuleType.EVIDENCE_VERIFIED,
                    verified("THESIS"), 2, source);
            rule(policy, root, "AI_CONTEST", "교내·외 공모전 참여", GraduationRuleType.EVIDENCE_VERIFIED,
                    verified("CONTEST_PARTICIPATION"), 3, source);
            rule(policy, root, "AI_CONTEST_AWARD", "교내·외 공모전 수상", GraduationRuleType.EVIDENCE_VERIFIED,
                    verified("CONTEST_AWARD"), 4, source);
        }
        if (!exists(organization, "HS_AI_CAPSTONE_2025")) {
            GraduationPolicy policy = policy(organization, unit, "HS_AI_CAPSTONE_2025", PolicyType.UNIT_GRADUATION,
                    "AI응용학과 인공지능 캡스톤디자인", (short) 2025, LocalDate.of(2027, 2, 1), actor);
            GraduationPolicySource source = source(policy, AI_URL, "AI응용학과 졸업요건 인정 신청 안내", LocalDate.of(2026, 6, 10));
            GraduationRequirement root = group(policy, null, "AI_CAPSTONE_ANY", "인공지능 캡스톤디자인", RequirementOperatorType.ANY, 1, true, source);
            rule(policy, root, "AI_CAPSTONE_COURSE", "인공지능 캡스톤디자인 이수", GraduationRuleType.COURSE_NAME_ANY,
                    "{\"courseNames\":[\"인공지능 캡스톤디자인\"]}", 1, source);
            rule(policy, root, "AI_CAPSTONE_EVIDENCE", "캡스톤 증빙", GraduationRuleType.EVIDENCE_VERIFIED,
                    verified("CAPSTONE"), 2, source);
        }
        if (!exists(organization, "HS_AI_PROJECT_2025")) {
            GraduationPolicy policy = policy(organization, unit, "HS_AI_PROJECT_2025", PolicyType.UNIT_GRADUATION,
                    "AI응용학과 기업연계형 프로젝트", (short) 2025, null, actor);
            GraduationPolicySource source = source(policy, AI_URL, "AI응용학과 졸업요건 인정 신청 안내", LocalDate.of(2026, 6, 10));
            GraduationRequirement root = group(policy, null, "AI_PROJECT_ANY", "기업연계 교과·프로젝트 중 1개", RequirementOperatorType.ANY, 1, true, source);
            rule(policy, root, "AI_PROJECT_COURSE", "기업연계 프로젝트형 교과", GraduationRuleType.COURSE_NAME_ANY,
                    "{\"courseNames\":[\"기업연계 AI캡스톤디자인\",\"산학협력 프로젝트\",\"산학협력프로젝트\"]}", 1, source);
            rule(policy, root, "AI_PROJECT_EVIDENCE", "산학협력 프로젝트 참여", GraduationRuleType.EVIDENCE_VERIFIED,
                    verified("INDUSTRY_PROJECT"), 2, source);
        }
    }

    private void createCsePolicy(Organization organization, AcademicUnit unit, User actor) {
        String code = "HS_CSE_PROJECT_2028";
        if (exists(organization, code)) return;
        GraduationPolicy policy = policy(organization, unit, code, PolicyType.UNIT_GRADUATION,
                "컴퓨터공학부 산학협력 프로젝트", null, LocalDate.of(2028, 2, 1), actor);
        GraduationPolicySource source = source(policy, CSE_URL, "컴퓨터공학부 학점 및 졸업요건 문의 답변", LocalDate.of(2026, 8, 5));
        GraduationRequirement root = group(policy, null, "CSE_PROJECT_ANY", "산학협력 프로젝트 이수", RequirementOperatorType.ANY, 1, true, source);
        rule(policy, root, "CSE_PROJECT_COURSE", "산학협력 프로젝트형 교과", GraduationRuleType.COURSE_NAME_ANY,
                "{\"courseNames\":[\"기업연계 SW캡스톤디자인\",\"산학협력 프로젝트\",\"산학협력프로젝트\"]}", 1, source);
        rule(policy, root, "CSE_PROJECT_EVIDENCE", "산학협력 프로젝트 참여", GraduationRuleType.EVIDENCE_VERIFIED,
                verified("INDUSTRY_PROJECT"), 2, source);
        rule(policy, root, "CSE_EMPLOYMENT", "재학 중 취업 경력", GraduationRuleType.EVIDENCE_VERIFIED,
                verified("EMPLOYMENT"), 3, source);
    }

    private GraduationPolicy policy(Organization organization, AcademicUnit unit, String code, PolicyType type,
                                    String title, Short admissionFrom, LocalDate expectedFrom, User actor) {
        return policyRepository.save(GraduationPolicy.builder()
                .organization(organization).academicUnit(unit).policyCode(code).versionNo(1).policyType(type)
                .title(title).admissionYearFrom(admissionFrom).effectiveFrom(LocalDate.of(2025, 1, 1))
                .expectedGraduationFrom(expectedFrom).status(PolicyStatus.PUBLISHED)
                .sourceSetHash(hash(code)).createdBy(actor).reviewedBy(actor)
                .reviewedAt(LocalDateTime.now()).publishedAt(LocalDateTime.now()).build());
    }

    private GraduationPolicySource source(GraduationPolicy policy, String url, String title, LocalDate publishedAt) {
        return sourceRepository.save(GraduationPolicySource.builder().policy(policy).sourceType(PolicySourceType.NOTICE)
                .officialUrl(url).title(title).publishedAt(publishedAt).retrievedAt(LocalDateTime.now())
                .contentHash(hash(url)).sourceLocator("공식 웹페이지").build());
    }

    private GraduationRequirement group(GraduationPolicy policy, GraduationRequirement parent, String code, String title,
                                        RequirementOperatorType operator, int sequence, boolean required, GraduationPolicySource source) {
        return requirementRepository.save(GraduationRequirement.builder().policy(policy).parent(parent)
                .requirementCode(code).title(title).nodeType(RequirementNodeType.GROUP).operatorType(operator)
                .parametersJson("{}").sequenceNo(sequence).required(required).source(source).build());
    }

    private void rule(GraduationPolicy policy, GraduationRequirement parent, String code, String title,
                      GraduationRuleType type, String parameters, int sequence, GraduationPolicySource source) {
        requirementRepository.save(GraduationRequirement.builder().policy(policy).parent(parent)
                .requirementCode(code).title(title).nodeType(RequirementNodeType.RULE).ruleType(type)
                .parametersJson(parameters).sequenceNo(sequence).required(false).source(source).build());
    }

    private boolean exists(Organization organization, String code) {
        return policyRepository.findByOrganizationIdAndPolicyCodeAndVersionNo(organization.getId(), code, 1).isPresent();
    }

    private String verified(String recordType) {
        return "{\"recordType\":\"" + recordType
                + "\",\"verificationStatuses\":[\"DOCUMENT_VERIFIED\",\"UNIVERSITY_VERIFIED\"]}";
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
