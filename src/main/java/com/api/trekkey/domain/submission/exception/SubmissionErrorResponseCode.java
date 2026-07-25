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
    SUBMISSION_FORBIDDEN(
            "SUBMISSION_FORBIDDEN",
            403,
            "본인 팀의 제출물만 관리할 수 있습니다."),
    SUBMISSION_NOT_OPEN(
            "SUBMISSION_NOT_OPEN",
            409,
            "현재 제출이 열려 있지 않은 대회입니다."),
    SUBMISSION_FINALIZED(
            "SUBMISSION_FINALIZED",
            409,
            "제출이 마감되어 수정할 수 없습니다."),
    SUBMISSION_FILE_REQUIRED(
            "SUBMISSION_FILE_REQUIRED",
            400,
            "제출 파일을 1개 이상 첨부해야 합니다."),
    SUBMISSION_FILE_TYPE_INVALID(
            "SUBMISSION_FILE_TYPE_INVALID",
            400,
            "허용되지 않는 파일 형식입니다."),
    SUBMISSION_FILE_NOT_FOUND(
            "SUBMISSION_FILE_NOT_FOUND",
            404,
            "제출 파일을 찾을 수 없습니다."),
    SUBMISSION_STORAGE_ERROR(
            "SUBMISSION_STORAGE_ERROR",
            500,
            "파일 저장 처리 중 오류가 발생했습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
