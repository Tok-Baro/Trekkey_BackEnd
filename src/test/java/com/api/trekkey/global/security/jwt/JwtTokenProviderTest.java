package com.api.trekkey.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.trekkey.global.security.AuthPrincipal;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class JwtTokenProviderTest {

    private JwtProperties properties;
    private JwtTokenProvider tokenProvider;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecretKey(Base64.getEncoder().encodeToString(
                "test-secret-key-must-be-long-enough-for-hs512-signature-1234567890"
                        .getBytes(StandardCharsets.UTF_8)));
        properties.setAccessExpiration(1_800L);
        properties.setRefreshExpiration(1_209_600L);

        tokenProvider = new JwtTokenProvider(properties);
        tokenProvider.afterPropertiesSet();

        AuthPrincipal principal = AuthPrincipal.of(1L, "hong@example.com", List.of("PARTICIPANT"));
        authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    @Test
    @DisplayName("access token은 사용자 ID와 권한을 포함하고 access 검증을 통과한다")
    void createAccessToken_containsAuthenticationAndPassesValidation() {
        String token = tokenProvider.createAccessToken(authentication);

        assertThat(tokenProvider.validateAccessToken(token)).isTrue();
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(1L);
        assertThat(tokenProvider.getAuthentication(token).getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_PARTICIPANT");
    }

    @Test
    @DisplayName("refresh token은 사용자 ID를 포함하고 refresh 검증을 통과한다")
    void createRefreshToken_containsUserIdAndPassesValidation() {
        String token = tokenProvider.createRefreshToken(authentication);

        assertThatCode(() -> tokenProvider.validateRefreshTokenOrThrow(token))
                .doesNotThrowAnyException();
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("refresh token은 access token으로 사용할 수 없다")
    void validateAccessToken_rejectsRefreshToken() {
        String refreshToken = tokenProvider.createRefreshToken(authentication);

        assertThat(tokenProvider.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("access token은 refresh token으로 사용할 수 없다")
    void validateRefreshToken_rejectsAccessToken() {
        String accessToken = tokenProvider.createAccessToken(authentication);

        assertThatThrownBy(() -> tokenProvider.validateRefreshTokenOrThrow(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("서명이 변조된 token은 검증을 통과할 수 없다")
    void validateAccessToken_rejectsTamperedToken() {
        String token = tokenProvider.createAccessToken(authentication);
        String[] parts = token.split("\\.");
        char firstSignatureCharacter = parts[2].charAt(0);
        char replacement = firstSignatureCharacter == 'a' ? 'b' : 'a';
        String tampered = parts[0] + "." + parts[1] + "."
                + replacement + parts[2].substring(1);

        assertThat(tokenProvider.validateAccessToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("JWT 형식이 아닌 문자열은 검증을 통과할 수 없다")
    void validateAccessToken_rejectsMalformedToken() {
        assertThat(tokenProvider.validateAccessToken("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("만료된 access token은 검증을 통과할 수 없다")
    void validateAccessToken_rejectsExpiredToken() {
        properties.setAccessExpiration(-1L);
        String expiredToken = tokenProvider.createAccessToken(authentication);

        assertThat(tokenProvider.validateAccessToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("만료된 refresh token은 검증을 통과할 수 없다")
    void validateRefreshToken_rejectsExpiredToken() {
        properties.setRefreshExpiration(-1L);
        String expiredToken = tokenProvider.createRefreshToken(authentication);

        assertThatThrownBy(() -> tokenProvider.validateRefreshTokenOrThrow(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("연속해서 발급한 refresh token은 고유한 jti로 서로 다른 값을 갖는다")
    void createRefreshToken_returnsUniqueTokens() {
        String first = tokenProvider.createRefreshToken(authentication);
        String second = tokenProvider.createRefreshToken(authentication);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("JWT secret이 없으면 애플리케이션 초기화를 거부한다")
    void initialization_rejectsMissingSecret() {
        JwtTokenProvider insecureProvider = new JwtTokenProvider(new JwtProperties());

        assertThatThrownBy(insecureProvider::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is required");
    }

    @Test
    @DisplayName("HS512에 부족한 JWT secret은 애플리케이션 초기화를 거부한다")
    void initialization_rejectsWeakSecret() {
        JwtProperties weakProperties = new JwtProperties();
        weakProperties.setSecretKey(Base64.getEncoder().encodeToString(
                "too-short".getBytes(StandardCharsets.UTF_8)));
        JwtTokenProvider insecureProvider = new JwtTokenProvider(weakProperties);

        assertThatThrownBy(insecureProvider::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 bytes");
    }
}
