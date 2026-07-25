package com.api.trekkey.domain.review.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 심사 링크 토큰 유틸 — 원문은 발급 응답에만 노출하고 해시만 저장한다 (초대 토큰과 동일 패턴)
@Component
public class ReviewTokenSupport {

    private final String frontBaseUrl;

    public ReviewTokenSupport(@Value("${app.front.base-url}") String frontBaseUrl) {
        this.frontBaseUrl = frontBaseUrl;
    }

    public String generateToken() {
        return UUID.randomUUID().toString();
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    public String buildReviewUrl(String rawToken) {
        return frontBaseUrl + "/review?token=" + rawToken;
    }
}
