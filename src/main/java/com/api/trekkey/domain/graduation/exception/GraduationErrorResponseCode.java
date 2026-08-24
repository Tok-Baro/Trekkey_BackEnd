package com.api.trekkey.domain.graduation.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GraduationErrorResponseCode implements BaseResponseCode {
    GRADUATION_PROFILE_NOT_CONFIGURED("GRADUATION_PROFILE_NOT_CONFIGURED", 400, "졸업요건 검사 전 학적 정보를 설정해주세요."),
    GRADUATION_PROFILE_INVALID("GRADUATION_PROFILE_INVALID", 400, "학적 정보 입력값을 확인해주세요."),
    GRADUATION_PROFILE_VERSION_CONFLICT("GRADUATION_PROFILE_VERSION_CONFLICT", 409, "다른 곳에서 학적 정보가 변경되었습니다. 새로고침 후 다시 시도해주세요."),
    GRADUATION_ACADEMIC_UNIT_NOT_FOUND("GRADUATION_ACADEMIC_UNIT_NOT_FOUND", 404, "선택한 학과 또는 트랙 정보를 찾을 수 없습니다."),
    GRADUATION_IMPORT_INVALID("GRADUATION_IMPORT_INVALID", 400, "성적표 파일을 읽을 수 없습니다. PDF 또는 CSV 형식을 확인해주세요."),
    GRADUATION_COURSE_NOT_FOUND("GRADUATION_COURSE_NOT_FOUND", 404, "과목 기록을 찾을 수 없습니다."),
    GRADUATION_UNSUPPORTED_ORGANIZATION("GRADUATION_UNSUPPORTED_ORGANIZATION", 403, "한성대학교 학생만 사용할 수 있습니다."),
    GRADUATION_POLICY_NOT_FOUND("GRADUATION_POLICY_NOT_FOUND", 404, "적용 가능한 졸업요건 정책을 찾을 수 없습니다."),
    GRADUATION_EVALUATION_INPUT_CHANGED("GRADUATION_EVALUATION_INPUT_CHANGED", 409, "검사 중 학적 입력이 변경되었습니다. 다시 검사해주세요."),
    GRADUATION_INVALID_POLICY_DATE("GRADUATION_INVALID_POLICY_DATE", 400, "미래 시점 정책으로 검사할 수 없습니다."),
    GRADUATION_RULE_INVALID("GRADUATION_RULE_INVALID", 500, "졸업요건 정책 규칙이 올바르지 않습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
