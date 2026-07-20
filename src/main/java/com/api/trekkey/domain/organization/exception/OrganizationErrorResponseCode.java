package com.api.trekkey.domain.organization.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrganizationErrorResponseCode implements BaseResponseCode {
    ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT("ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT", 400, "학교이름을 2글자 이상 입력해주세요"),
    ORGANIZATION_NOT_FOUND("ORGANIZATION_NOT_FOUND", 404, "가입 가능한 학교를 찾을 수 없습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
