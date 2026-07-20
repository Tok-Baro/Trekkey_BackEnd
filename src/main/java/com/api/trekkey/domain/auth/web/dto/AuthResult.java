package com.api.trekkey.domain.auth.web.dto;

public record AuthResult(
        UserSignInRes userSignInRes,
        String refreshToken
) {
}
