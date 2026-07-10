package com.api.trekkey.domain.user.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserErrorResponseCode implements BaseResponseCode {
    USER_EXISTS_EMAIL("USER_EXISTS_EMAIL", 409, "이미 해당 이메일이 존재합니다");

    private final String code;
    private final int httpStatus;
    private final String message;

}
