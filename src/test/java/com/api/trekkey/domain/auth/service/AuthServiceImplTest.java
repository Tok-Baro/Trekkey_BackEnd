package com.api.trekkey.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.auth.entity.RefreshToken;
import com.api.trekkey.domain.auth.repository.RefreshTokenRepository;
import com.api.trekkey.domain.invitation.entity.AdminInvitation;
import com.api.trekkey.domain.invitation.entity.InvitationStatus;
import com.api.trekkey.domain.invitation.exception.AdminInvitationErrorResponseCode;
import com.api.trekkey.domain.invitation.repository.AdminInvitationRepository;
import com.api.trekkey.domain.invitation.web.dto.request.AdminSignUpReq;
import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSignInReq;
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
import com.api.trekkey.global.security.jwt.JwtProperties;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import com.api.trekkey.global.security.jwt.TokenDto;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
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

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private AdminInvitationRepository adminInvitationRepository;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                organizationRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                jwtProperties,
                adminInvitationRepository,
                adminAuditLogger
        );
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

    @Test
    @DisplayName("유효한 자격으로 로그인하면 사용자 세션과 해시된 refresh token을 저장한다")
    void signIn_returnsSessionAndStoresHashedRefreshToken() {
        User user = user(1L, UserStatus.ACTIVE, passwordEncoder.encode("password123"));
        UserSignInReq request = signInRequest("hong@example.com", "password123");
        given(userRepository.findByEmail("hong@example.com")).willReturn(Optional.of(user));
        given(jwtTokenProvider.createTokens(any())).willReturn(TokenDto.bearer("access-token", "refresh-token"));
        given(jwtProperties.getRefreshExpiration()).willReturn(1_209_600L);

        AuthResult result = authService.signIn(request);

        assertThat(result.userSignInRes().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.userSignInRes().userSessionRes().id()).isEqualTo(1L);
        assertThat(result.userSignInRes().userSessionRes().name()).isEqualTo("홍길동");
        assertThat(result.userSignInRes().userSessionRes().email()).isEqualTo("hong@example.com");
        assertThat(result.userSignInRes().userSessionRes().role()).isEqualTo(UserRole.PARTICIPANT);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getFamilyId()).isNotBlank().hasSize(36);
        assertThat(savedToken.getTokenHash()).isEqualTo(sha256("refresh-token")).hasSize(64);
        assertThat(savedToken.getTokenHash()).isNotEqualTo(result.refreshToken());
        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 이메일은 동일한 자격 오류를 반환한다")
    void signIn_throwsWhenEmailDoesNotExist() {
        given(userRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        assertInvalidCredentials(signInRequest("missing@example.com", "password123"));

        verifyNoInteractions(refreshTokenRepository, jwtTokenProvider);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 token을 발급하지 않는다")
    void signIn_throwsWhenPasswordDoesNotMatch() {
        User user = user(1L, UserStatus.ACTIVE, passwordEncoder.encode("password123"));
        given(userRepository.findByEmail("hong@example.com")).willReturn(Optional.of(user));

        assertInvalidCredentials(signInRequest("hong@example.com", "wrong-password"));

        verifyNoInteractions(refreshTokenRepository, jwtTokenProvider);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 사용자는 올바른 비밀번호로도 로그인할 수 없다")
    void signIn_throwsWhenUserIsNotActive() {
        User user = user(1L, UserStatus.INACTIVE, passwordEncoder.encode("password123"));
        given(userRepository.findByEmail("hong@example.com")).willReturn(Optional.of(user));

        assertInvalidCredentials(signInRequest("hong@example.com", "password123"));

        verifyNoInteractions(refreshTokenRepository, jwtTokenProvider);
    }

    @Test
    @DisplayName("refresh token 재발급은 기존 token을 폐기하고 같은 family에 새 token 해시를 저장한다")
    void reissue_revokesCurrentTokenAndStoresReplacementInSameFamily() {
        User user = user(1L, UserStatus.ACTIVE, "encoded-password");
        RefreshToken current = refreshToken(user, "family-id", "old-refresh", false, LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByTokenHash(sha256("old-refresh"))).willReturn(Optional.of(current));
        given(jwtTokenProvider.getUserIdFromToken("old-refresh")).willReturn(1L);
        given(jwtTokenProvider.createTokens(any())).willReturn(TokenDto.bearer("new-access", "new-refresh"));
        given(jwtProperties.getRefreshExpiration()).willReturn(1_209_600L);

        AuthResult result = authService.reissue("old-refresh");

        assertThat(current.isRevoked()).isTrue();
        assertThat(result.userSignInRes().accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getFamilyId()).isEqualTo("family-id");
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(sha256("new-refresh"));
    }

    @Test
    @DisplayName("빈 refresh token은 DB를 조회하지 않고 거부한다")
    void reissue_throwsWhenRefreshTokenIsBlank() {
        assertInvalidToken(() -> authService.reissue(" "));
        verifyNoInteractions(refreshTokenRepository, jwtTokenProvider);
    }

    @Test
    @DisplayName("DB에 없는 refresh token은 거부한다")
    void reissue_throwsWhenRefreshTokenIsNotStored() {
        given(refreshTokenRepository.findByTokenHash(sha256("unknown"))).willReturn(Optional.empty());

        assertInvalidToken(() -> authService.reissue("unknown"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("폐기된 refresh token이 재사용되면 family 전체를 폐기한다")
    void reissue_revokesFamilyWhenRevokedTokenIsReused() {
        User user = user(1L, UserStatus.ACTIVE, "encoded-password");
        RefreshToken reused = refreshToken(user, "family-id", "reused", true, LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByTokenHash(sha256("reused"))).willReturn(Optional.of(reused));

        assertInvalidToken(() -> authService.reissue("reused"));

        verify(refreshTokenRepository).revokeAllByFamilyId("family-id");
        verify(refreshTokenRepository, never()).save(any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("JWT 검증에 실패한 refresh token은 family 전체를 폐기한다")
    void reissue_revokesFamilyWhenJwtValidationFails() {
        User user = user(1L, UserStatus.ACTIVE, "encoded-password");
        RefreshToken saved = refreshToken(user, "family-id", "invalid-jwt", false, LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByTokenHash(sha256("invalid-jwt"))).willReturn(Optional.of(saved));
        willThrow(new JwtException("invalid")).given(jwtTokenProvider).validateRefreshTokenOrThrow("invalid-jwt");

        assertInvalidToken(() -> authService.reissue("invalid-jwt"));

        verify(refreshTokenRepository).revokeAllByFamilyId("family-id");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("DB 만료 시각이 지난 refresh token은 family 전체를 폐기한다")
    void reissue_revokesFamilyWhenDatabaseTokenIsExpired() {
        User user = user(1L, UserStatus.ACTIVE, "encoded-password");
        RefreshToken expired = refreshToken(user, "family-id", "expired", false, LocalDateTime.now().minusSeconds(1));
        given(refreshTokenRepository.findByTokenHash(sha256("expired"))).willReturn(Optional.of(expired));

        assertInvalidToken(() -> authService.reissue("expired"));

        verify(refreshTokenRepository).revokeAllByFamilyId("family-id");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("JWT 사용자와 DB token 소유자가 다르면 family 전체를 폐기한다")
    void reissue_revokesFamilyWhenUserIdDoesNotMatch() {
        User user = user(1L, UserStatus.ACTIVE, "encoded-password");
        RefreshToken saved = refreshToken(user, "family-id", "mismatch", false, LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByTokenHash(sha256("mismatch"))).willReturn(Optional.of(saved));
        given(jwtTokenProvider.getUserIdFromToken("mismatch")).willReturn(2L);

        assertInvalidToken(() -> authService.reissue("mismatch"));

        verify(refreshTokenRepository).revokeAllByFamilyId("family-id");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자가 비활성화되면 refresh token family 전체를 폐기한다")
    void reissue_revokesFamilyWhenUserIsNotActive() {
        User user = user(1L, UserStatus.INACTIVE, "encoded-password");
        RefreshToken saved = refreshToken(user, "family-id", "inactive", false, LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByTokenHash(sha256("inactive"))).willReturn(Optional.of(saved));
        given(jwtTokenProvider.getUserIdFromToken("inactive")).willReturn(1L);

        assertInvalidToken(() -> authService.reissue("inactive"));

        verify(refreshTokenRepository).revokeAllByFamilyId("family-id");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그아웃은 현재 refresh token의 family 전체를 폐기한다")
    void logout_revokesRefreshTokenFamily() {
        User user = user(1L, UserStatus.ACTIVE, "encoded-password");
        RefreshToken saved = refreshToken(user, "family-id", "refresh", false, LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByTokenHash(sha256("refresh"))).willReturn(Optional.of(saved));

        authService.logout("refresh");

        verify(refreshTokenRepository).revokeAllByFamilyId("family-id");
    }

    @Test
    @DisplayName("빈 refresh token으로 로그아웃해도 성공한다")
    void logout_doesNothingWhenRefreshTokenIsBlank() {
        authService.logout(" ");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("DB에 없는 refresh token으로 로그아웃해도 성공한다")
    void logout_doesNothingWhenRefreshTokenIsNotStored() {
        given(refreshTokenRepository.findByTokenHash(sha256("unknown")))
                .willReturn(Optional.empty());

        authService.logout("unknown");

        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any());
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

    private UserSignInReq signInRequest(String email, String password) {
        UserSignInReq request = new UserSignInReq();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private User user(Long id, UserStatus status, String password) {
        return User.builder()
                .id(id)
                .organization(mock(Organization.class))
                .name("홍길동")
                .email("hong@example.com")
                .password(password)
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(status)
                .studentId("20240001")
                .major("컴퓨터공학부")
                .build();
    }

    private RefreshToken refreshToken(
            User user,
            String familyId,
            String rawToken,
            boolean revoked,
            LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .user(user)
                .familyId(familyId)
                .tokenHash(sha256(rawToken))
                .expiresAt(expiresAt)
                .revoked(revoked)
                .build();
    }

    private void assertInvalidCredentials(UserSignInReq request) {
        assertThatThrownBy(() -> authService.signIn(request))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_INVALID_CREDENTIALS);
    }

    private void assertInvalidToken(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
