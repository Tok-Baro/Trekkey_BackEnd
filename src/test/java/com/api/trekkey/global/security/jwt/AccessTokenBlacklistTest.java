package com.api.trekkey.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.response.code.ErrorResponseCode;
import com.api.trekkey.global.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class AccessTokenBlacklistTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private JwtProperties properties;
    private JwtTokenProvider tokens;
    private AccessTokenBlacklist blacklist;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecretKey(Base64.getEncoder().encodeToString("x".repeat(64).getBytes(StandardCharsets.UTF_8)));
        properties.setAccessExpiration(900L);
        properties.setRefreshExpiration(604800L);
        tokens = new JwtTokenProvider(properties);
        tokens.afterPropertiesSet();
        blacklist = new AccessTokenBlacklist(redis, tokens);
        AuthPrincipal principal = AuthPrincipal.of(1L, "test@example.com", List.of("PARTICIPANT"));
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void 액세스_토큰의_해시를_남은_유효기간만큼_저장한다() {
        String token = tokens.createAccessToken(authentication);
        long before = tokens.getExpiration(token);
        when(redis.opsForValue()).thenReturn(values);

        blacklist.revoke(token);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(key.capture(), eq("revoked"), ttl.capture());
        assertThat(key.getValue()).matches("trekkey:auth:access-blacklist:[a-f0-9]{64}");
        assertThat(ttl.getValue().toMillis()).isBetween(tokens.getExpiration(token), before);
        when(redis.hasKey(key.getValue())).thenReturn(true);
        assertThat(blacklist.contains(token)).isTrue();
        assertThat(blacklist.contains(tokens.createAccessToken(authentication))).isFalse();
    }

    @Test
    void 누락되거나_잘못되거나_만료된_토큰과_리프레시_토큰은_저장하지_않는다() {
        blacklist.revoke(null);
        blacklist.revoke(" ");
        blacklist.revoke("not-a-jwt");
        blacklist.revoke(tokens.createRefreshToken(authentication));
        String valid = tokens.createAccessToken(authentication);
        String[] parts = valid.split("\\.");
        blacklist.revoke(parts[0] + "." + parts[1] + "." + (parts[2].startsWith("a") ? "b" : "a") + parts[2].substring(1));
        properties.setAccessExpiration(-1L);
        blacklist.revoke(tokens.createAccessToken(authentication));
        verifyNoInteractions(redis);
    }

    @Test
    void 동등한_서명_인코딩의_토큰도_같은_블랙리스트_키로_차단한다() {
        String token = tokens.createAccessToken(authentication);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int last = alphabet.indexOf(token.charAt(token.length() - 1));
        String variant = token.substring(0, token.length() - 1) + alphabet.charAt(last ^ 1);
        assertThat(tokens.validateAccessToken(variant)).isTrue();
        when(redis.opsForValue()).thenReturn(values);
        blacklist.revoke(token);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), eq("revoked"), any(Duration.class));
        when(redis.hasKey(key.getValue())).thenReturn(true);
        assertThat(blacklist.contains(variant)).isTrue();
    }

    @Test
    void 레디스_장애가_발생하면_인증과_로그아웃을_거절한다() {
        String token = tokens.createAccessToken(authentication);
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new RedisConnectionFailureException("offline")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        assertThatThrownBy(() -> blacklist.contains(token)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getBaseResponseCode()).isEqualTo(ErrorResponseCode.AUTH_STORAGE_UNAVAILABLE));
        assertThatThrownBy(() -> blacklist.revoke(token)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getBaseResponseCode()).isEqualTo(ErrorResponseCode.AUTH_STORAGE_UNAVAILABLE));
    }
}
