package com.api.trekkey.domain.evidence.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EvidenceErrorResponseCode implements BaseResponseCode {
    EVIDENCE_NOT_FOUND("EVIDENCE_NOT_FOUND", 404, "외부 증빙을 찾을 수 없습니다."),
    EVIDENCE_CASE_NOT_FOUND("EVIDENCE_CASE_NOT_FOUND", 404, "검증 건을 찾을 수 없습니다."),
    EVIDENCE_FILE_NOT_FOUND("EVIDENCE_FILE_NOT_FOUND", 404, "증빙 파일을 찾을 수 없습니다."),
    EVIDENCE_FILE_REQUIRED("EVIDENCE_FILE_REQUIRED", 400, "증빙 파일을 첨부해주세요."),
    EVIDENCE_FILE_TOO_LARGE("EVIDENCE_FILE_TOO_LARGE", 400, "증빙 파일은 10MB 이하여야 합니다."),
    EVIDENCE_FILE_BUNDLE_TOO_LARGE("EVIDENCE_FILE_BUNDLE_TOO_LARGE", 400, "증빙은 최대 5개, 합계 25MB까지 제출할 수 있습니다."),
    EVIDENCE_FILE_DUPLICATE("EVIDENCE_FILE_DUPLICATE", 400, "같은 증빙 파일을 중복 제출할 수 없습니다."),
    EVIDENCE_FILE_TYPE_INVALID("EVIDENCE_FILE_TYPE_INVALID", 400, "PDF, JPEG, PNG 원본만 제출할 수 있습니다."),
    EVIDENCE_STORAGE_ERROR("EVIDENCE_STORAGE_ERROR", 500, "증빙 파일 저장 중 오류가 발생했습니다."),
    EVIDENCE_RECORD_TYPE_INVALID("EVIDENCE_RECORD_TYPE_INVALID", 400, "증빙 유형과 졸업 비교과 항목이 맞지 않습니다."),
    EVIDENCE_INVALID_DATE("EVIDENCE_INVALID_DATE", 400, "증빙 만료일은 발급일보다 빠를 수 없습니다."),
    EVIDENCE_EXPIRED("EVIDENCE_EXPIRED", 422, "이미 만료된 증빙은 승인할 수 없습니다."),
    EVIDENCE_PROFILE_REQUIRED("EVIDENCE_PROFILE_REQUIRED", 400, "외부 증빙 제출 전 학적 프로필을 설정해주세요."),
    EVIDENCE_ADMIN_REQUIRED("EVIDENCE_ADMIN_REQUIRED", 403, "관리자만 외부 증빙을 검수할 수 있습니다."),
    EVIDENCE_REVIEW_CLOSED("EVIDENCE_REVIEW_CLOSED", 409, "이미 최종 판정된 검증 건입니다."),
    EVIDENCE_REVIEWER_CONFLICT("EVIDENCE_REVIEWER_CONFLICT", 409, "같은 관리자가 두 번 검수하거나 본인 제출물을 검수할 수 없습니다."),
    EVIDENCE_REVIEW_LIMIT("EVIDENCE_REVIEW_LIMIT", 409, "이미 필요한 검수 의견이 모두 등록되었습니다."),
    EVIDENCE_INVALID_REVIEW("EVIDENCE_INVALID_REVIEW", 400, "수동 검수는 L2 강도로만 판정할 수 있습니다."),
    EVIDENCE_INVALID_REFERENCE("EVIDENCE_INVALID_REFERENCE", 400, "승인 시 HTTPS 공식 확인 URL을 입력해주세요.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
