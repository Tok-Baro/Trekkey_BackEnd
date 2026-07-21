package com.api.trekkey.domain.contest.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ContestErrorResponseCode implements BaseResponseCode {
    CONTEST_NOT_FOUND("CONTEST_NOT_FOUND", 404, "대회를 찾을 수 없습니다."),
    CONTEST_FORBIDDEN("CONTEST_FORBIDDEN", 403, "해당 대회에 대한 권한이 없습니다."),
    STAGE_NOT_FOUND("STAGE_NOT_FOUND", 404, "평가 단계를 찾을 수 없습니다."),
    CONTEST_STAGE_REQUIRED("CONTEST_STAGE_REQUIRED", 400, "대회에는 최소 1개의 단계가 필요합니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
