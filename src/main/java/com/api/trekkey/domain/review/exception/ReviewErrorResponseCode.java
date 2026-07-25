package com.api.trekkey.domain.review.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReviewErrorResponseCode implements BaseResponseCode {
    JUDGE_NOT_FOUND(
            "JUDGE_NOT_FOUND",
            404,
            "심사위원을 찾을 수 없습니다."),
    JUDGE_HAS_ASSIGNMENTS(
            "JUDGE_HAS_ASSIGNMENTS",
            409,
            "배정이 있는 심사위원은 삭제할 수 없습니다."),
    REVIEW_TOKEN_INVALID(
            "REVIEW_TOKEN_INVALID",
            404,
            "심사 링크를 찾을 수 없습니다."),
    REVIEW_TOKEN_EXPIRED(
            "REVIEW_TOKEN_EXPIRED",
            401,
            "만료된 심사 링크입니다. 관리자에게 재발급을 요청하세요."),
    ROUND_NOT_REVIEW_STAGE(
            "ROUND_NOT_REVIEW_STAGE",
            400,
            "심사 단계가 아닌 라운드입니다."),
    ROUND_ALREADY_OPENED(
            "ROUND_ALREADY_OPENED",
            409,
            "이미 시작된 라운드입니다."),
    ROUND_NOT_OPEN(
            "ROUND_NOT_OPEN",
            409,
            "심사가 진행 중인 라운드가 아닙니다."),
    ROUND_NO_TARGET(
            "ROUND_NO_TARGET",
            400,
            "라운드에 진입할 제출물이 없습니다."),
    ROUND_NO_JUDGE(
            "ROUND_NO_JUDGE",
            400,
            "심사위원을 먼저 등록해야 라운드를 시작할 수 있습니다."),
    ENTRY_NOT_FOUND(
            "ENTRY_NOT_FOUND",
            404,
            "심사 대상을 찾을 수 없습니다."),
    ENTRY_ALREADY_FINALIZED(
            "ENTRY_ALREADY_FINALIZED",
            409,
            "이미 확정된 심사 대상입니다."),
    ASSIGNMENT_NOT_FOUND(
            "ASSIGNMENT_NOT_FOUND",
            404,
            "심사 배정을 찾을 수 없습니다."),
    REVIEW_ALREADY_SUBMITTED(
            "REVIEW_ALREADY_SUBMITTED",
            409,
            "이미 제출한 심사는 수정할 수 없습니다."),
    SCORE_CRITERION_MISMATCH(
            "SCORE_CRITERION_MISMATCH",
            400,
            "모든 평가 항목의 점수를 정확히 입력해야 합니다."),
    SCORE_OUT_OF_RANGE(
            "SCORE_OUT_OF_RANGE",
            400,
            "평가 항목의 점수 범위를 벗어났습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
