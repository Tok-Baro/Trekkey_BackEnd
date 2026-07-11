package com.api.trekkey.domain.auth.web.dto;

public record UserSignInRes(
        String accessToken,
        UserSessionRes userSessionRes
) {
}
