package com.api.trekkey.domain.graduation.integration;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.graduation.entity.*;
import com.api.trekkey.domain.graduation.repository.GraduationEvaluationItemRepository;
import com.api.trekkey.domain.graduation.repository.GraduationEvaluationRepository;
import com.api.trekkey.domain.graduation.service.GraduationEvaluationServiceImpl;
import com.api.trekkey.domain.graduation.web.controller.GraduationEvaluationController;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.auto_quote_keyword=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({GraduationEvaluationServiceImpl.class, ObjectMapper.class})
class GraduationEvaluationPostIntegrationTest {
    @Autowired private TestEntityManager entityManager;
    @Autowired private GraduationEvaluationServiceImpl service;
    @Autowired private GraduationEvaluationRepository evaluationRepository;
    @Autowired private GraduationEvaluationItemRepository itemRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "code", "HANSUNG_UNIVERSITY");
        ReflectionTestUtils.setField(organization, "name", "한성대학교");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        organization = entityManager.persist(organization);
        User student = entityManager.persist(User.builder().organization(organization).name("학생")
                .email("student-post@test.com").password("encoded").role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT).status(UserStatus.ACTIVE).studentId("2021001").build());
        User admin = entityManager.persist(User.builder().organization(organization).name("관리자")
                .email("admin-post@test.com").password("encoded").role(UserRole.ADMIN)
                .memberType(MemberType.STAFF).status(UserStatus.ACTIVE).build());
        entityManager.persist(StudentAcademicProfile.builder().user(student).admissionYear((short) 2021)
                .curriculumYear((short) 2021).admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR).majorPlanType(MajorPlanType.CONVERGENCE_I)
                .registeredSemesters((short) 8).totalCredits(new BigDecimal("130.0"))
                .hansungCredits(new BigDecimal("130.0")).transferRecognizedCredits(BigDecimal.ZERO)
                .cumulativeGpa(new BigDecimal("3.50")).gpaScale(new BigDecimal("4.5")).activityPoints(800)
                .inputMode(InputMode.SUMMARY_ONLY).recordCompleteness(RecordCompleteness.UNKNOWN)
                .failHistoryStatus(FailHistoryStatus.NONE).build());
        GraduationPolicy policy = entityManager.persist(GraduationPolicy.builder().organization(organization)
                .policyCode("HANSUNG-POST-INTEGRATION").versionNo(1).policyType(PolicyType.COMMON)
                .title("POST 통합 정책").effectiveFrom(LocalDate.of(2020, 1, 1))
                .status(PolicyStatus.PUBLISHED).sourceSetHash("b".repeat(64)).createdBy(admin)
                .reviewedBy(admin).reviewedAt(LocalDateTime.now()).publishedAt(LocalDateTime.now()).build());
        entityManager.persist(GraduationRequirement.builder().policy(policy).requirementCode("TOTAL_CREDITS")
                .title("총 이수학점").nodeType(RequirementNodeType.RULE)
                .ruleType(GraduationRuleType.TOTAL_CREDITS_MIN).parametersJson("{\"min\":130}")
                .sequenceNo(1).required(true).build());
        entityManager.flush();

        AuthPrincipal principal = AuthPrincipal.of(student.getId(), student.getEmail(), List.of("PARTICIPANT"));
        mockMvc = MockMvcBuilders.standaloneSetup(new GraduationEvaluationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver(principal)).build();
    }

    @Test
    void postRunsRealServiceAndPersistsEvaluationAndItems() throws Exception {
        mockMvc.perform(post("/api/me/graduation/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyAsOf\":\"2026-08-11\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.summary.satisfied").value(1))
                .andExpect(jsonPath("$.data.requirements[0].code").value("TOTAL_CREDITS"))
                .andExpect(jsonPath("$.data.requirements[0].status").value("SATISFIED"));

        assertThat(evaluationRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(1);
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(AuthPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return AuthPrincipal.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }
}
