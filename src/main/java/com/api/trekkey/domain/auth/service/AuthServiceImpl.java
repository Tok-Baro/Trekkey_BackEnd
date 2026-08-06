package com.api.trekkey.domain.auth.service;

import com.api.trekkey.domain.auth.entity.RefreshToken;
import com.api.trekkey.domain.auth.repository.RefreshTokenRepository;
import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSignInReq;
import com.api.trekkey.domain.auth.web.dto.UserSignInRes;
import com.api.trekkey.domain.auth.web.dto.UserSessionRes;
import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.invitation.entity.AdminInvitation;
import com.api.trekkey.domain.invitation.exception.AdminInvitationErrorResponseCode;
import com.api.trekkey.domain.invitation.repository.AdminInvitationRepository;
import com.api.trekkey.domain.invitation.web.dto.request.AdminSignUpReq;
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
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.jwt.JwtProperties;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import com.api.trekkey.global.security.jwt.TokenDto;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    // 로그인 잠금 정책 (설계 §3-1): 계정 기준 연속 5회 실패 → 15분 잠금
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final AdminInvitationRepository adminInvitationRepository;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    public void signUp(UserSignUpReq userSignUpReq) {

        // 사용자가 입력한 이메일이 이미 존재하는지 확인
        if (userRepository.existsByEmail(userSignUpReq.getEmail())) {
            throw new CustomException(UserErrorResponseCode.USER_EXISTS_EMAIL);
        }

        // 같은 학교 안에서 학번 중복 확인 — DB 복합 유일 제약(uk_user_organization_student_id)의 사전 검증
        if (userSignUpReq.getStudentId() != null && !userSignUpReq.getStudentId().isBlank()
                && userRepository.existsByOrganizationIdAndStudentId(
                        userSignUpReq.getOrganizationId(), userSignUpReq.getStudentId())) {
            throw new CustomException(UserErrorResponseCode.USER_EXISTS_STUDENT_ID);
        }

        // 사용자가 선택한 학교가 실제로 활성상태인지 확인
        Organization organization = organizationRepository.findByIdAndStatus(
                        userSignUpReq.getOrganizationId(),
                        OrganizationStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(OrganizationErrorResponseCode.ORGANIZATION_NOT_FOUND));

        User user = User.builder()
                .organization(organization)
                .email(userSignUpReq.getEmail())
                .name(userSignUpReq.getName())
                .password(passwordEncoder.encode(userSignUpReq.getPassword()))
                .studentId(userSignUpReq.getStudentId())
                .major(userSignUpReq.getMajor())
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
    }

    // noRollbackFor: ISSUED인데 만료된 초대를 EXPIRED로 lazy 전이한 뒤 예외를 던져도 전이가 커밋되도록 한다.
    @Override
    @Transactional(noRollbackFor = CustomException.class)
    public void signUpAdmin(AdminSignUpReq adminSignUpReq) {
        LocalDateTime now = LocalDateTime.now();

        // 초대 토큰은 원문 미저장 — 해시로 조회한다. (RefreshToken과 동일 패턴)
        AdminInvitation invitation = adminInvitationRepository.findByTokenHash(hash(adminSignUpReq.inviteToken()))
                .orElseThrow(() -> new CustomException(AdminInvitationErrorResponseCode.INVITATION_INVALID));

        validateInvitationUsable(invitation, now);

        // 초대 이메일 일치 검증 — 어떤 검증이 실패했는지 구분 노출하지 않는다 (INVITATION_INVALID로 통일)
        if (!invitation.getEmail().equals(adminSignUpReq.email())) {
            throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_INVALID);
        }

        if (userRepository.existsByEmail(adminSignUpReq.email())) {
            throw new CustomException(UserErrorResponseCode.USER_EXISTS_EMAIL);
        }

        // 가입 직후 상태 = PENDING_APPROVAL → ROOT_ADMIN 승인 전까지 로그인 불가 (설계 §2 게이트 3)
        User user = User.builder()
                .organization(invitation.getOrganization())
                .email(adminSignUpReq.email())
                .name(adminSignUpReq.name())
                .password(passwordEncoder.encode(adminSignUpReq.password()))
                .department(adminSignUpReq.department())
                .position(adminSignUpReq.position())
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.PENDING_APPROVAL)
                .build();

        userRepository.save(user);
        invitation.use(now);

        adminAuditLogger.log(
                user.getId(),
                invitation.getOrganization().getId(),
                AuditAction.ADMIN_SIGNUP,
                "USER",
                user.getId(),
                adminSignUpReq.email()
        );
    }

    // noRollbackFor: 실패 카운트 증가·잠금 기록이 CustomException에도 롤백되지 않고 커밋되어야 한다. (reissue와 동일 패턴)
    @Override
    @Transactional(noRollbackFor = CustomException.class)
    public AuthResult signIn(UserSignInReq userSignInReq) {
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findByEmail(userSignInReq.getEmail())
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_INVALID_CREDENTIALS));

        // 잠금 중 시도 → 423. 미존재 이메일은 위에서 이미 동일한 자격 오류로 응답 (존재 여부 비노출, 설계 §3-1)
        if (user.isLocked(now)) {
            throw new CustomException(UserErrorResponseCode.USER_ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(userSignInReq.getPassword(), user.getPassword())) {
            handleFailedLogin(user, now);
            throw new CustomException(UserErrorResponseCode.USER_INVALID_CREDENTIALS);
        }

        // 승인 대기 관리자는 구분된 에러로 안내 — 본인은 자기 가입 사실을 알므로 정보 노출 아님 (설계 §2-3)
        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            throw new CustomException(UserErrorResponseCode.USER_PENDING_APPROVAL);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(UserErrorResponseCode.USER_INVALID_CREDENTIALS);
        }

        user.resetLoginFailure();

        return issueTokens(user, UUID.randomUUID().toString());
    }

    @Override
    @Transactional(noRollbackFor = CustomException.class)
    public AuthResult reissue(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
        }

        // 쿠키로 받은 refresh token 원문은 DB에 저장하지 않는다.
        // 해시값으로 토큰을 찾되, 폐기된 토큰도 조회해서 재사용 공격을 감지한다.
        RefreshToken savedToken = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN));

        if (savedToken.isRevoked()) {
            refreshTokenRepository.revokeAllByFamilyId(savedToken.getFamilyId());
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
        }

        try {
            jwtTokenProvider.validateRefreshTokenOrThrow(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            refreshTokenRepository.revokeAllByFamilyId(savedToken.getFamilyId());
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
        }

        if (savedToken.isExpired(LocalDateTime.now())) {
            refreshTokenRepository.revokeAllByFamilyId(savedToken.getFamilyId());
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
        }

        Long tokenUserId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = savedToken.getUser();

        if (!user.getId().equals(tokenUserId) || user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllByFamilyId(savedToken.getFamilyId());
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
        }

        // Rotation: 재발급에 성공한 refresh token은 즉시 폐기하고 새 refresh token을 저장한다.
        // 그래서 탈취된 예전 refresh token을 다시 보내도 재사용할 수 없다.
        savedToken.revoke();

        return issueTokens(user, savedToken.getFamilyId());
    }

    @Override
    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        // 로그아웃은 쿠키 삭제와 DB 토큰 폐기가 함께 일어나야 한다.
        // 같은 로그인 세션의 refresh token family 전체를 폐기해서 동시 요청으로 새 토큰이 생겨도 함께 끊는다.
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(savedToken -> refreshTokenRepository.revokeAllByFamilyId(savedToken.getFamilyId()));
    }


    //======= 헬퍼 메서드 ==========

    // 초대가 가입에 사용 가능한 상태인지 검증한다. ISSUED인데 만료 시각이 지났으면 EXPIRED로 전이까지 수행한다.
    private void validateInvitationUsable(AdminInvitation invitation, LocalDateTime now) {
        switch (invitation.getStatus()) {
            case USED -> throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_ALREADY_USED);
            case REVOKED -> throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_INVALID);
            case EXPIRED -> throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_EXPIRED);
            case ISSUED -> {
                if (invitation.isExpired(now)) {
                    invitation.expire();
                    throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_EXPIRED);
                }
            }
        }
    }

    // 로그인 실패 카운트를 올리고, 임계치 도달 시 계정을 잠그고 감사 로그를 남긴다. (설계 §3-1)
    private void handleFailedLogin(User user, LocalDateTime now) {
        if (user.increaseFailedLogin() >= MAX_FAILED_ATTEMPTS) {
            user.lock(now.plusMinutes(LOCK_MINUTES));
            adminAuditLogger.log(
                    user.getId(),
                    user.getOrganization().getId(),
                    AuditAction.LOGIN_LOCKED,
                    "USER",
                    user.getId(),
                    "연속 " + MAX_FAILED_ATTEMPTS + "회 실패 잠금"
            );
        }
    }

    private AuthResult issueTokens(User user, String familyId) {
        Authentication authentication = createAuthentication(user);
        TokenDto tokenDto = jwtTokenProvider.createTokens(authentication);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(tokenDto.refreshToken()))
                .familyId(familyId)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration()))
                .build();

        refreshTokenRepository.save(refreshToken);

        UserSignInRes response = new UserSignInRes(
                tokenDto.accessToken(),
                UserSessionRes.from(user)
        );

        return new AuthResult(response, tokenDto.refreshToken());
    }

    private Authentication createAuthentication(User user) {
        AuthPrincipal principal = AuthPrincipal.of(
                user.getId(),
                user.getEmail(),
                List.of(user.getRole().name())
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
