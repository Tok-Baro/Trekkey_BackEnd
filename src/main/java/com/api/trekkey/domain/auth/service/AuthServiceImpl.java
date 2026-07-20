package com.api.trekkey.domain.auth.service;

import com.api.trekkey.domain.auth.entity.RefreshToken;
import com.api.trekkey.domain.auth.repository.RefreshTokenRepository;
import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSignInReq;
import com.api.trekkey.domain.auth.web.dto.UserSignInRes;
import com.api.trekkey.domain.auth.web.dto.UserSessionRes;
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
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Override
    public void signUp(UserSignUpReq userSignUpReq) {

        // 사용자가 입력한 이메일이 이미 존재하는지 확인
        if (userRepository.existsByEmail(userSignUpReq.getEmail())) {
            throw new CustomException(UserErrorResponseCode.USER_EXISTS_EMAIL);
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

    @Override
    public AuthResult signIn(UserSignInReq userSignInReq) {

        User user = userRepository.findByEmail(userSignInReq.getEmail())
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_INVALID_CREDENTIALS));

        if (user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(userSignInReq.getPassword(), user.getPassword())) {
            throw new CustomException(UserErrorResponseCode.USER_INVALID_CREDENTIALS);
        }

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
