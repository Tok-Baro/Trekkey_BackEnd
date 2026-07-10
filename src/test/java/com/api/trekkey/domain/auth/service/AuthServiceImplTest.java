package com.api.trekkey.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.domain.user.web.dto.UserSignUpReq;
import com.api.trekkey.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(userRepository, passwordEncoder, organizationRepository);
    }

    @Test
    @DisplayName("ACTIVE 학교를 선택한 회원은 암호화된 비밀번호와 학생 참여자 상태로 저장된다")
    void signUp_savesActiveStudentParticipant() {
        UserSignUpReq request = signUpRequest(1L, "홍길동", "hong@example.com", "password123", "20240001", "컴퓨터공학부");
        Organization organization = mock(Organization.class);
        given(userRepository.existsByEmail("hong@example.com")).willReturn(false);
        given(organizationRepository.findByIdAndStatus(1L, OrganizationStatus.ACTIVE))
                .willReturn(Optional.of(organization));

        authService.signUp(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).existsByEmail("hong@example.com");
        verify(organizationRepository).findByIdAndStatus(1L, OrganizationStatus.ACTIVE);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getOrganization()).isSameAs(organization);
        assertThat(savedUser.getEmail()).isEqualTo("hong@example.com");
        assertThat(savedUser.getName()).isEqualTo("홍길동");
        assertThat(savedUser.getStudentId()).isEqualTo("20240001");
        assertThat(savedUser.getMajor()).isEqualTo("컴퓨터공학부");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.PARTICIPANT);
        assertThat(savedUser.getMemberType()).isEqualTo(MemberType.STUDENT);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 학교 조회와 저장 없이 예외를 던진다")
    void signUp_throwsWhenEmailAlreadyExists() {
        UserSignUpReq request = signUpRequest(1L, "홍길동", "hong@example.com", "password123", null, null);
        given(userRepository.existsByEmail("hong@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_EXISTS_EMAIL);

        verify(userRepository).existsByEmail("hong@example.com");
        verifyNoInteractions(organizationRepository);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("존재하지 않거나 비활성인 학교면 회원을 저장하지 않고 예외를 던진다")
    void signUp_throwsWhenOrganizationIsNotActive() {
        UserSignUpReq request = signUpRequest(1L, "홍길동", "hong@example.com", "password123", null, null);
        given(userRepository.existsByEmail("hong@example.com")).willReturn(false);
        given(organizationRepository.findByIdAndStatus(1L, OrganizationStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(OrganizationErrorResponseCode.ORGANIZATION_NOT_FOUND);

        verify(userRepository).existsByEmail("hong@example.com");
        verify(organizationRepository).findByIdAndStatus(1L, OrganizationStatus.ACTIVE);
        verifyNoMoreInteractions(userRepository);
    }

    private UserSignUpReq signUpRequest(
            Long organizationId,
            String name,
            String email,
            String password,
            String studentId,
            String major) {
        UserSignUpReq request = new UserSignUpReq();
        ReflectionTestUtils.setField(request, "organizationId", organizationId);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "studentId", studentId);
        ReflectionTestUtils.setField(request, "major", major);
        return request;
    }
}
