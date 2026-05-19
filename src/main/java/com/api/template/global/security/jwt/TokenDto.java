package com.api.template.global.security.jwt;

public record TokenDto(
        String grantType,
        String accessToken,
        String refreshToken
) {

    public static TokenDto bearer(String accessToken, String refreshToken) {
        return new TokenDto("Bearer", accessToken, refreshToken);
    }
}
