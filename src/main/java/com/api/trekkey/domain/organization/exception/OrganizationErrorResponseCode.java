package com.api.trekkey.domain.organization.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrganizationErrorResponseCode implements BaseResponseCode {
    ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT("ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT", 400, "학교이름을 2글자 이상 입력해주세요");

    private final String code;
    private final int httpStatus;
    private final String message;
}
