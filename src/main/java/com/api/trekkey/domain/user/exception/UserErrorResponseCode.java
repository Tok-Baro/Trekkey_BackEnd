package com.api.trekkey.domain.user.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserErrorResponseCode implements BaseResponseCode {
    USER_EXISTS_EMAIL("USER_EXISTS_EMAIL", 409, "이미 해당 이메일이 존재합니다"),
    USER_NOT_FOUND("USER_NOT_FOUND", 404, "사용자를 찾을 수 없습니다"),
    USER_INVALID_CREDENTIALS("USER_INVALID_CREDENTIALS",401,"이메일 또는 비밀번호가 올바르지 않습니다"),
    USER_INVALID_TOKEN("USER_INVALID_TOKEN",401, "로그인이 만료되었습니다");

    private final String code;
    private final int httpStatus;
    private final String message;

}
