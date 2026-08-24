package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.exception.GraduationErrorResponseCode;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.graduation.repository.AcademicUnitRepository;
import com.api.trekkey.domain.graduation.repository.StudentAcademicUnitRepository;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileReq;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileRes;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GraduationProfileServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private StudentAcademicProfileRepository profileRepository;
    @Mock private AcademicUnitRepository academicUnitRepository;
    @Mock private StudentAcademicUnitRepository academicUnitSelectionRepository;

    private GraduationProfileServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "id", 1L);
        ReflectionTestUtils.setField(organization, "publicId", "hansung-public-id");
        ReflectionTestUtils.setField(organization, "code", "HANSUNG_UNIVERSITY");
        ReflectionTestUtils.setField(organization, "name", "한성대학교");
        user = User.builder()
                .organization(organization)
                .name("테스트 학생")
                .email("student@test.com")
                .password("encoded")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId("2699001")
                .build();
        ReflectionTestUtils.setField(user, "id", 11L);
        service = new GraduationProfileServiceImpl(
                userRepository, profileRepository, academicUnitRepository, academicUnitSelectionRepository);
        given(userRepository.findById(11L)).willReturn(Optional.of(user));
    }

    @Test
    void returnsConfiguredFalseWhenProfileDoesNotExist() {
        given(profileRepository.findByUserId(11L)).willReturn(Optional.empty());

        GraduationProfileRes result = service.get(11L);

        assertThat(result.configured()).isFalse();
        assertThat(result.studentNumber()).isEqualTo("2699001");
        assertThat(result.organization().name()).isEqualTo("한성대학교");
    }

    @Test
    void createsAcademicProfileForCurrentUser() {
        given(profileRepository.findByUserId(11L)).willReturn(Optional.empty());
        given(profileRepository.saveAndFlush(any())).willAnswer(invocation -> {
            StudentAcademicProfile profile = invocation.getArgument(0);
            ReflectionTestUtils.setField(profile, "publicId", "profile-public-id");
            ReflectionTestUtils.setField(profile, "version", 0L);
            return profile;
        });

        GraduationProfileRes result = service.save(11L, request(null, true));

        assertThat(result.configured()).isTrue();
        assertThat(result.totalCredits()).isEqualByComparingTo("130.0");
        assertThat(result.recordCompleteness()).isEqualTo(RecordCompleteness.COMPLETE);
        verify(profileRepository).saveAndFlush(any(StudentAcademicProfile.class));
    }

    @Test
    void rejectsStaleVersionWhenUpdating() {
        StudentAcademicProfile profile = profile();
        ReflectionTestUtils.setField(profile, "version", 3L);
        given(profileRepository.findByUserId(11L)).willReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.save(11L, request(2L, true)))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode())
                        .isEqualTo(GraduationErrorResponseCode.GRADUATION_PROFILE_VERSION_CONFLICT));
    }

    @Test
    void requiresExplicitConfirmationBeforeMarkingComplete() {
        given(profileRepository.findByUserId(11L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(11L, request(null, false)))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode())
                        .isEqualTo(GraduationErrorResponseCode.GRADUATION_PROFILE_INVALID));
    }

    private GraduationProfileReq request(Long version, boolean confirmed) {
        return new GraduationProfileReq(
                2022, 2022, AdmissionType.FRESHMAN, GraduationPath.REGULAR, MajorPlanType.INTENSIVE,
                8, new BigDecimal("130.0"), new BigDecimal("118.0"), BigDecimal.ZERO,
                new BigDecimal("3.85"), new BigDecimal("4.5"), 120, false, false,
                InputMode.SUMMARY_ONLY, RecordCompleteness.COMPLETE, "2026-2",
                2027, 2, java.util.List.of(), FailHistoryStatus.NONE,
                confirmed, version);
    }

    private StudentAcademicProfile profile() {
        return StudentAcademicProfile.builder()
                .user(user)
                .admissionYear((short) 2022)
                .curriculumYear((short) 2022)
                .admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR)
                .majorPlanType(MajorPlanType.INTENSIVE)
                .registeredSemesters((short) 8)
                .totalCredits(new BigDecimal("130.0"))
                .hansungCredits(new BigDecimal("118.0"))
                .transferRecognizedCredits(BigDecimal.ZERO)
                .cumulativeGpa(new BigDecimal("3.85"))
                .gpaScale(new BigDecimal("4.5"))
                .activityPoints(120)
                .internationalStudent(false)
                .teachingProgram(false)
                .inputMode(InputMode.SUMMARY_ONLY)
                .recordCompleteness(RecordCompleteness.COMPLETE)
                .summaryAsOfTerm("2026-2")
                .failHistoryStatus(FailHistoryStatus.NONE)
                .build();
    }
}
