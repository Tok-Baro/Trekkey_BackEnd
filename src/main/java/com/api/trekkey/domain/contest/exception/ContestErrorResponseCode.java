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
    STAGE_DUPLICATED("STAGE_DUPLICATED", 409, "같은 단계가 요청에 중복되어 있습니다."),
    SUBMISSION_STAGE_DUPLICATED(
            "SUBMISSION_STAGE_DUPLICATED",
            409,
            "제출 단계는 대회에 하나만 설정할 수 있습니다."),
    CONTEST_STAGE_REQUIRED("CONTEST_STAGE_REQUIRED", 400, "대회에는 최소 1개의 단계가 필요합니다."),
    STAGE_CONFIGURATION_LOCKED(
            "STAGE_CONFIGURATION_LOCKED",
            409,
            "진행 중이거나 완료된 단계의 설정은 변경할 수 없습니다."),
    STAGE_CONFIGURATION_INVALID(
            "STAGE_CONFIGURATION_INVALID",
            409,
            "단계를 시작하기 위한 설정이 올바르지 않습니다."),
    INVALID_STAGE_STATUS_TRANSITION(
            "INVALID_STAGE_STATUS_TRANSITION",
            409,
            "허용되지 않는 단계 상태 변경입니다."),
    REVIEW_CRITERION_NOT_FOUND(
            "REVIEW_CRITERION_NOT_FOUND",
            404,
            "평가 기준을 찾을 수 없습니다."),
    REVIEW_CRITERION_NOT_ALLOWED(
            "REVIEW_CRITERION_NOT_ALLOWED",
            400,
            "심사 또는 발표 단계에만 평가 기준을 설정할 수 있습니다."),
    REVIEW_CRITERION_INVALID(
            "REVIEW_CRITERION_INVALID",
            400,
            "평가 기준의 코드, 이름, 배점 또는 순서가 올바르지 않습니다."),
    REVIEW_CRITERION_CODE_IMMUTABLE(
            "REVIEW_CRITERION_CODE_IMMUTABLE",
            409,
            "저장된 평가 기준 코드는 변경할 수 없습니다."),
    REVIEW_CRITERION_DUPLICATED(
            "REVIEW_CRITERION_DUPLICATED",
            409,
            "같은 단계에 중복된 평가 기준이 있습니다."),
    REVIEW_CRITERION_REQUIRED(
            "REVIEW_CRITERION_REQUIRED",
            409,
            "심사 단계를 시작하려면 활성 평가 기준이 하나 이상 필요합니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
