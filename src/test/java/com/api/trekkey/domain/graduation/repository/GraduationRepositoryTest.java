package com.api.trekkey.domain.graduation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.graduation.entity.*;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.auto_quote_keyword=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class GraduationRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private StudentAcademicProfileRepository profileRepository;
    @Autowired private StudentCourseRecordRepository courseRecordRepository;

    @Test
    void graduationEntitiesAreRegisteredInTheJpaMetamodel() {
        Set<Class<?>> entityTypes = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType())
                .collect(Collectors.toSet());

        assertThat(entityTypes).contains(
                AcademicUnit.class,
                AcademicCourse.class,
                GraduationPolicy.class,
                GraduationPolicySource.class,
                GraduationRequirement.class,
                StudentAcademicProfile.class,
                StudentAcademicUnit.class,
                StudentCourseRecord.class,
                StudentNonCourseRecord.class,
                GraduationEvaluation.class,
                GraduationEvaluationPolicy.class,
                GraduationEvaluationItem.class);
    }

    @Test
    void studentRecordsAreOnlyResolvedInsideTheAuthenticatedUserScope() {
        Organization organization = entityManager.persist(organization("한성대학교"));
        User student = entityManager.persist(user(organization, "student@test.com", "2026001"));
        User otherStudent = entityManager.persist(user(organization, "other@test.com", "2026002"));
        StudentAcademicProfile profile = entityManager.persist(profile(student));
        StudentCourseRecord record = entityManager.persist(StudentCourseRecord.builder()
                .profile(profile)
                .term("2026-1")
                .courseCode("HSW1001")
                .courseName("AI와 SW 기초")
                .credits(new BigDecimal("3.0"))
                .grade("A+")
                .completionStatus(CourseCompletionStatus.COMPLETED)
                .category(CourseCategory.GENERAL_REQUIRED)
                .mappingStatus(CourseMappingStatus.SELF_REPORTED)
                .sourceType(RecordSourceType.MANUAL)
                .build());
        entityManager.flush();
        entityManager.clear();

        assertThat(profileRepository.findByUserId(student.getId())).isPresent();
        assertThat(courseRecordRepository.findByPublicIdAndProfileUserId(record.getPublicId(), student.getId()))
                .isPresent();
        assertThat(courseRecordRepository.findByPublicIdAndProfileUserId(record.getPublicId(), otherStudent.getId()))
                .isEmpty();
    }

    private Organization organization(String name) {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "name", name);
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        return organization;
    }

    private User user(Organization organization, String email, String studentId) {
        return User.builder()
                .organization(organization)
                .name("학생")
                .email(email)
                .password("encoded-password")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId(studentId)
                .build();
    }

    private StudentAcademicProfile profile(User user) {
        return StudentAcademicProfile.builder()
                .user(user)
                .admissionYear((short) 2021)
                .curriculumYear((short) 2021)
                .admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR)
                .majorPlanType(MajorPlanType.CONVERGENCE_I)
                .registeredSemesters((short) 8)
                .totalCredits(new BigDecimal("130.0"))
                .hansungCredits(new BigDecimal("130.0"))
                .transferRecognizedCredits(BigDecimal.ZERO)
                .cumulativeGpa(new BigDecimal("3.50"))
                .gpaScale(new BigDecimal("4.5"))
                .activityPoints(800)
                .inputMode(InputMode.COURSE_DETAIL)
                .recordCompleteness(RecordCompleteness.COMPLETE)
                .summaryAsOfTerm("2026-1")
                .failHistoryStatus(FailHistoryStatus.NONE)
                .build();
    }
}
