package com.api.trekkey.domain.graduation.integration;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.graduation.entity.AcademicUnit;
import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.entity.StudentCourseRecord;
import com.api.trekkey.domain.graduation.repository.AcademicUnitRepository;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.graduation.repository.StudentCourseRecordRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real repositories/security/MVC, OSIV disabled, and deliberately no enclosing test transaction. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:graduation-course-http-tx;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "security.jwt.secret-key=c2VjdXJpdHktY29uZmlnLXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtbG9uZy0xMjM0NTY3ODkwYWJjZGVm",
        "security.jwt.access-expiration=900", "security.jwt.refresh-expiration=604800",
        "app.front.base-url=http://localhost:3000", "app.cors.allowed-origin=http://localhost:3000",
        "app.evidence.lookup-hmac-secret=synthetic-course-http-hmac-secret-over-thirty-two-bytes",
        "blockchain.anchoring.mode=DISABLED", "blockchain.anchoring.worker-enabled=false"
})
@AutoConfigureMockMvc
class GraduationCourseHttpTransactionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired StudentAcademicProfileRepository profiles;
    @Autowired AcademicUnitRepository units;
    @Autowired StudentCourseRecordRepository courses;
    @Autowired PlatformTransactionManager transactionManager;
    private Fixture fixture;

    @BeforeEach
    void seedInSeparateCommittedTransaction() {
        fixture = new TransactionTemplate(transactionManager).execute(ignored -> {
            Organization ownOrg = organization();
            Organization otherOrg = organization();
            User student = user(ownOrg);
            User otherStudent = user(ownOrg);
            AcademicUnit ownUnit = unit(ownOrg);
            AcademicUnit foreignUnit = unit(otherOrg);
            StudentAcademicProfile profile = profiles.saveAndFlush(StudentAcademicProfile.builder().user(student)
                    .admissionYear((short) 2021).curriculumYear((short) 2021).admissionType(AdmissionType.FRESHMAN)
                    .graduationPath(GraduationPath.REGULAR).majorPlanType(MajorPlanType.CONVERGENCE_I)
                    .registeredSemesters((short) 8).totalCredits(new BigDecimal("130.0"))
                    .hansungCredits(new BigDecimal("130.0")).transferRecognizedCredits(BigDecimal.ZERO)
                    .cumulativeGpa(new BigDecimal("3.50")).gpaScale(new BigDecimal("4.5"))
                    .activityPoints(800).inputMode(InputMode.COURSE_DETAIL).recordCompleteness(RecordCompleteness.PARTIAL)
                    .failHistoryStatus(FailHistoryStatus.NONE).build());
            StudentCourseRecord course = courses.saveAndFlush(StudentCourseRecord.builder().profile(profile)
                    .term("2021-1").courseCode("SYN-TX-001").courseName("Synthetic mapped course")
                    .credits(new BigDecimal("3.0")).grade("A+").completionStatus(CourseCompletionStatus.COMPLETED)
                    .category(CourseCategory.MAJOR_ELECTIVE).academicUnit(ownUnit)
                    .mappingStatus(CourseMappingStatus.SELF_REPORTED).sourceType(RecordSourceType.MANUAL).build());
            return new Fixture(student.getId(), token(student), token(otherStudent), course.getPublicId(),
                    ownUnit.getPublicId(), foreignUnit.getPublicId());
        });
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void listResolvesMappedAcademicUnitAfterRepositoryCallReturns() throws Exception {
        mvc.perform(get("/api/me/graduation/courses").header("Authorization", fixture.studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].academicUnitPublicId").value(fixture.ownUnitId()))
                .andExpect(jsonPath("$.data[0].academicUnitName").value("Synthetic transaction unit"));
    }

    @Test
    void patchResolvesProfileTenantAndReturnsCommittedMappingWithOsivDisabled() throws Exception {
        mvc.perform(patch("/api/me/graduation/courses/{id}", fixture.courseId())
                        .header("Authorization", fixture.studentToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapping(fixture.ownUnitId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("MAJOR_REQUIRED"))
                .andExpect(jsonPath("$.data.academicUnitPublicId").value(fixture.ownUnitId()));
        mvc.perform(get("/api/me/graduation/courses").header("Authorization", fixture.studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("MAJOR_REQUIRED"))
                .andExpect(jsonPath("$.data[0].academicUnitPublicId").value(fixture.ownUnitId()));
        assertThat(courses.findByPublicIdAndProfileUserId(fixture.courseId(), fixture.studentId()).orElseThrow().getCategory())
                .isEqualTo(CourseCategory.MAJOR_REQUIRED);
    }

    @Test
    void foreignOrganizationUnitIsNotFoundAndDoesNotChangeStoredMapping() throws Exception {
        mvc.perform(patch("/api/me/graduation/courses/{id}", fixture.courseId())
                        .header("Authorization", fixture.studentToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapping(fixture.foreignUnitId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GRADUATION_ACADEMIC_UNIT_NOT_FOUND"));
        assertThat(courses.findByPublicIdAndProfileUserId(fixture.courseId(), fixture.studentId()).orElseThrow().getCategory())
                .isEqualTo(CourseCategory.MAJOR_ELECTIVE);
    }

    @Test
    void differentStudentCannotReadOrPatchOwnersCourse() throws Exception {
        mvc.perform(get("/api/me/graduation/courses").header("Authorization", fixture.otherStudentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(patch("/api/me/graduation/courses/{id}", fixture.courseId())
                        .header("Authorization", fixture.otherStudentToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapping(fixture.ownUnitId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GRADUATION_COURSE_NOT_FOUND"));
        assertThat(courses.findByPublicIdAndProfileUserId(fixture.courseId(), fixture.studentId()).orElseThrow().getCategory())
                .isEqualTo(CourseCategory.MAJOR_ELECTIVE);
    }

    private Organization organization() {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "code", "COURSE_TX_" + UUID.randomUUID());
        ReflectionTestUtils.setField(organization, "name", "Synthetic course transaction organization");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        return organizations.saveAndFlush(organization);
    }
    private User user(Organization organization) {
        return users.saveAndFlush(User.builder().organization(organization).name("Synthetic course student")
                .email(UUID.randomUUID() + "@example.invalid").password("unused-token-only-fixture")
                .role(UserRole.PARTICIPANT).memberType(MemberType.STUDENT).status(UserStatus.ACTIVE).build());
    }
    private AcademicUnit unit(Organization organization) {
        return units.saveAndFlush(AcademicUnit.builder().organization(organization).externalCode("SYN-TX")
                .name("Synthetic transaction unit").unitType(AcademicUnitType.TRACK).validFromYear((short) 2021)
                .status(AcademicUnitStatus.ACTIVE).build());
    }
    private String token(User user) {
        AuthPrincipal principal = AuthPrincipal.of(user.getId(), user.getEmail(), List.of("PARTICIPANT"));
        return "Bearer " + tokenProvider.createAccessToken(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }
    private String mapping(String academicUnitId) {
        return "{\"category\":\"MAJOR_REQUIRED\",\"academicUnitPublicId\":\"" + academicUnitId + "\"}";
    }
    private record Fixture(Long studentId, String studentToken, String otherStudentToken,
                           String courseId, String ownUnitId, String foreignUnitId) { }
}
