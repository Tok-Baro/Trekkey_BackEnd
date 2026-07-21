package com.api.trekkey.domain.contest.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ContestErrorResponseCode implements BaseResponseCode {
    CONTEST_NOT_FOUND("CONTEST_NOT_FOUND", 404, "대회를 찾을 수 없습니다");

    private final String code;
    private final int httpStatus;
    private final String message;
}
