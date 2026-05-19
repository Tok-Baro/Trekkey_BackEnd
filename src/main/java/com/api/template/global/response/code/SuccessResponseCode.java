package com.api.template.global.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessResponseCode implements BaseResponseCode {
    SUCCESS_OK("SUCCESS_200", 200, "요청이 성공적으로 처리되었습니다."),
    SUCCESS_CREATED("SUCCESS_201", 201, "요청이 성공적으로 생성되었습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
