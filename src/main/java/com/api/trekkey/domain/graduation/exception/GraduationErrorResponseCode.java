package com.api.trekkey.domain.graduation.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GraduationErrorResponseCode implements BaseResponseCode {
    GRADUATION_PROFILE_NOT_CONFIGURED("GRADUATION_PROFILE_NOT_CONFIGURED", 400, "졸업요건 검사 전 학적 정보를 설정해주세요."),
    GRADUATION_UNSUPPORTED_ORGANIZATION("GRADUATION_UNSUPPORTED_ORGANIZATION", 403, "한성대학교 학생만 사용할 수 있습니다."),
    GRADUATION_POLICY_NOT_FOUND("GRADUATION_POLICY_NOT_FOUND", 404, "적용 가능한 졸업요건 정책을 찾을 수 없습니다."),
    GRADUATION_EVALUATION_INPUT_CHANGED("GRADUATION_EVALUATION_INPUT_CHANGED", 409, "검사 중 학적 입력이 변경되었습니다. 다시 검사해주세요."),
    GRADUATION_INVALID_POLICY_DATE("GRADUATION_INVALID_POLICY_DATE", 400, "미래 시점 정책으로 검사할 수 없습니다."),
    GRADUATION_RULE_INVALID("GRADUATION_RULE_INVALID", 500, "졸업요건 정책 규칙이 올바르지 않습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
