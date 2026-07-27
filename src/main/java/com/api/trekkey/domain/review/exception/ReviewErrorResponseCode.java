package com.api.trekkey.domain.review.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReviewErrorResponseCode implements BaseResponseCode {
    CONTEST_JUDGE_NOT_FOUND(
            "CONTEST_JUDGE_NOT_FOUND",
            404,
            "심사위원을 찾을 수 없습니다."),
    CONTEST_JUDGE_DUPLICATED(
            "CONTEST_JUDGE_DUPLICATED",
            409,
            "이미 등록된 심사위원입니다."),
    CONTEST_JUDGE_USER_INVALID(
            "CONTEST_JUDGE_USER_INVALID",
            400,
            "심사위원으로 연결할 수 없는 사용자입니다."),
    REVIEW_LINK_EXPIRATION_INVALID(
            "REVIEW_LINK_EXPIRATION_INVALID",
            400,
            "심사 링크 만료 시각은 현재보다 이후여야 합니다."),
    REVIEW_LINK_INVALID(
            "REVIEW_LINK_INVALID",
            401,
            "유효하지 않거나 만료된 심사 링크입니다."),
    REVIEW_STAGE_INVALID(
            "REVIEW_STAGE_INVALID",
            409,
            "심사 대상을 설정할 수 있는 단계가 아닙니다."),
    REVIEW_ENTRY_PREPARATION_NOT_ALLOWED(
            "REVIEW_ENTRY_PREPARATION_NOT_ALLOWED",
            409,
            "준비 중인 심사 단계에서만 심사 대상을 생성할 수 있습니다."),
    REVIEW_ENTRY_CONTEST_NOT_REVIEWING(
            "REVIEW_ENTRY_CONTEST_NOT_REVIEWING",
            409,
            "대회가 심사 중인 상태에서만 심사 대상을 준비하거나 심사 단계를 시작할 수 있습니다."),
    REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED(
            "REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED",
            409,
            "현재 심사 대상 선정 방식은 아직 지원하지 않습니다."),
    REVIEW_ENTRY_SUBMISSION_STAGE_NOT_COMPLETED(
            "REVIEW_ENTRY_SUBMISSION_STAGE_NOT_COMPLETED",
            409,
            "제출 단계가 완료된 후 심사 대상을 준비할 수 있습니다."),
    REVIEW_ENTRY_SUBMISSION_REQUIRED(
            "REVIEW_ENTRY_SUBMISSION_REQUIRED",
            409,
            "심사 대상으로 등록할 제출 완료 작품이 없습니다."),
    REVIEW_ENTRY_SUBMISSION_INVALID(
            "REVIEW_ENTRY_SUBMISSION_INVALID",
            409,
            "심사 대상으로 확정할 수 없는 제출물이 포함되어 있습니다."),
    REVIEW_ENTRY_DUPLICATED(
            "REVIEW_ENTRY_DUPLICATED",
            409,
            "심사 대상이 이미 등록되어 있습니다."),
    REVIEW_ENTRY_REQUIRED(
            "REVIEW_ENTRY_REQUIRED",
            409,
            "심사 단계를 시작하려면 심사 대상을 먼저 준비해야 합니다."),
    REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED(
            "REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED",
            409,
            "준비 중이거나 진행 중인 심사 단계에서만 심사위원을 배정할 수 있습니다."),
    REVIEW_ASSIGNMENT_ENTRY_REQUIRED(
            "REVIEW_ASSIGNMENT_ENTRY_REQUIRED",
            409,
            "심사위원에게 배정할 심사 대상이 없습니다."),
    REVIEW_ASSIGNMENT_ENTRY_INVALID(
            "REVIEW_ASSIGNMENT_ENTRY_INVALID",
            409,
            "현재 상태에서는 배정할 수 없는 심사 대상이 포함되어 있습니다."),
    REVIEW_ASSIGNMENT_DUE_AT_INVALID(
            "REVIEW_ASSIGNMENT_DUE_AT_INVALID",
            400,
            "심사 배정 마감 시각이 올바르지 않습니다."),
    REVIEW_ASSIGNMENT_DUPLICATED(
            "REVIEW_ASSIGNMENT_DUPLICATED",
            409,
            "심사 대상 배정이 이미 처리되었습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
