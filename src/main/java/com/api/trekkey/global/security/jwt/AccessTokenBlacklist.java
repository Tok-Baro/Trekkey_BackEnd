package com.api.trekkey.global.security.jwt;

import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.response.code.ErrorResponseCode;
import io.jsonwebtoken.ExpiredJwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AccessTokenBlacklist {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public void revoke(String token) {
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateAccessToken(token)) {
            return;
        }

        long remainingMillis;
        try {
            remainingMillis = jwtTokenProvider.getExpiration(token);
        } catch (ExpiredJwtException exception) {
            // 검증 직후 만료된 토큰은 이미 인증할 수 없으므로 저장하지 않는다.
            return;
        }
        if (remainingMillis <= 0) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(key(token), "revoked", Duration.ofMillis(remainingMillis));
        } catch (DataAccessException exception) {
            throw new CustomException(ErrorResponseCode.AUTH_STORAGE_UNAVAILABLE);
        }
    }

    // 반드시 서명·종류·만료 검증을 통과한 access token으로 호출한다.
    public boolean contains(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)));
        } catch (DataAccessException exception) {
            throw new CustomException(ErrorResponseCode.AUTH_STORAGE_UNAVAILABLE);
        }
    }

    private String key(String token) {
        // 서명부의 동등한 Base64URL 표현으로 차단을 우회하지 못하도록 서명 대상(header.payload)을 해시한다.
        String signedContent = token.substring(0, token.lastIndexOf('.'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signedContent.getBytes(StandardCharsets.UTF_8));
            return "trekkey:auth:access-blacklist:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }
}
