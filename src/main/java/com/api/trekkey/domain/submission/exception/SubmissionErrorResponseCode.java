package com.api.trekkey.domain.submission.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SubmissionErrorResponseCode implements BaseResponseCode {
    SUBMISSION_NOT_FOUND(
            "SUBMISSION_NOT_FOUND",
            404,
            "제출물을 찾을 수 없습니다."),
    SUBMISSION_TEAM_NOT_FOUND(
            "SUBMISSION_TEAM_NOT_FOUND",
            404,
            "해당 대회에 참가한 팀을 찾을 수 없습니다."),
    SUBMISSION_TEAM_NOT_APPROVED(
            "SUBMISSION_TEAM_NOT_APPROVED",
            409,
            "승인된 참가 팀만 제출물을 작성할 수 있습니다."),
    SUBMISSION_STAGE_INVALID(
            "SUBMISSION_STAGE_INVALID",
            409,
            "대회의 제출 단계 설정이 올바르지 않습니다."),
    SUBMISSION_NOT_OPEN(
            "SUBMISSION_NOT_OPEN",
            409,
            "현재 제출물을 작성하거나 변경할 수 있는 기간이 아닙니다."),
    SUBMISSION_ALREADY_EXISTS(
            "SUBMISSION_ALREADY_EXISTS",
            409,
            "해당 팀의 제출물이 이미 존재합니다."),
    INVALID_SUBMISSION_STATUS_TRANSITION(
            "INVALID_SUBMISSION_STATUS_TRANSITION",
            409,
            "현재 제출물 상태에서는 요청한 작업을 수행할 수 없습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
