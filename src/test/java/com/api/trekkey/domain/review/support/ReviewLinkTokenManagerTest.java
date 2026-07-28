package com.api.trekkey.domain.review.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewLinkTokenManagerTest {

    private final ReviewLinkTokenManager tokenManager = new ReviewLinkTokenManager();

    @Test
    @DisplayName("심사 링크 토큰은 256비트 URL-safe 난수로 매번 다르게 생성된다")
    void generateToken_createsUniqueUrlSafeToken() {
        String first = tokenManager.generateToken();
        String second = tokenManager.generateToken();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("토큰 해시는 결정적인 64자리 SHA-256 hex이며 원문을 포함하지 않는다")
    void hash_returnsSha256Hex() {
        String first = tokenManager.hash("review-token");
        String second = tokenManager.hash("review-token");

        assertThat(first)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(second)
                .doesNotContain("review-token");
    }
}
