package com.api.trekkey.domain.credential.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CredentialErrorResponseCode implements BaseResponseCode {
    CREDENTIAL_NOT_FOUND("CREDENTIAL_404", 404, "존재하지 않는 Credential입니다."),
    CREDENTIAL_NUMBER_ALREADY_EXISTS(
            "CREDENTIAL_409_NUMBER",
            409,
            "해당 기관에서 이미 사용 중인 Credential 번호입니다."),
    CREDENTIAL_SOURCE_CONFLICT(
            "CREDENTIAL_409_SOURCE_CONFLICT",
            409,
            "같은 발급 원천으로 생성된 Credential과 요청 내용이 일치하지 않습니다."),
    BATCH_NOT_FOUND("CREDENTIAL_BATCH_404", 404, "존재하지 않는 앵커링 배치입니다."),
    STATUS_EVENT_NOT_FOUND("CREDENTIAL_STATUS_EVENT_404", 404, "존재하지 않는 상태 변경 요청입니다."),
    ISSUER_KEY_NOT_FOUND("CREDENTIAL_ISSUER_KEY_404", 404, "등록된 발급 기관 키가 없습니다."),
    NO_READY_CREDENTIAL("CREDENTIAL_BATCH_409_EMPTY", 409, "배치에 포함할 준비된 Credential이 없습니다."),
    INVALID_CREDENTIAL_STATE("CREDENTIAL_409_STATE", 409, "현재 Credential 상태에서는 요청을 처리할 수 없습니다."),
    INVALID_BATCH_STATE("CREDENTIAL_BATCH_409_STATE", 409, "현재 배치 상태에서는 요청을 처리할 수 없습니다."),
    APPROVAL_EXPIRED("CREDENTIAL_APPROVAL_409_EXPIRED", 409, "발급 기관 승인 요청이 만료되었습니다."),
    APPROVAL_RENEWAL_NOT_DUE("CREDENTIAL_APPROVAL_409_RENEWAL_NOT_DUE", 409, "현재 승인 요청은 아직 갱신할 수 없습니다."),
    INVALID_ISSUER_SIGNATURE("CREDENTIAL_APPROVAL_400_SIGNATURE", 400, "발급 기관 서명이 올바르지 않습니다."),
    ISSUER_KEY_MISMATCH("CREDENTIAL_ISSUER_KEY_409_MISMATCH", 409, "온체인 발급 기관 키와 요청 값이 일치하지 않습니다."),
    BLOCKCHAIN_READ_DISABLED("BLOCKCHAIN_503_READ_DISABLED", 503, "블록체인 조회 기능이 비활성화되어 있습니다."),
    BLOCKCHAIN_WRITE_DISABLED("BLOCKCHAIN_503_WRITE_DISABLED", 503, "블록체인 전송 기능이 비활성화되어 있습니다."),
    BLOCKCHAIN_CONFIGURATION_INVALID("BLOCKCHAIN_500_CONFIGURATION", 500, "블록체인 연결 설정이 올바르지 않습니다."),
    BLOCKCHAIN_RPC_UNAVAILABLE("BLOCKCHAIN_503_RPC", 503, "블록체인 네트워크를 조회할 수 없습니다."),
    BLOCKCHAIN_TRANSACTION_FAILED("BLOCKCHAIN_502_TRANSACTION", 502, "블록체인 트랜잭션 처리에 실패했습니다."),
    ANCHOR_EVIDENCE_MISMATCH("BLOCKCHAIN_409_EVIDENCE", 409, "저장된 증빙과 온체인 앵커가 일치하지 않습니다."),
    BLOCKCHAIN_RENEWAL_CONFLICT("BLOCKCHAIN_409_RENEWAL_CONFLICT", 409, "온체인 상태 때문에 승인 갱신을 진행할 수 없습니다."),
    BLOCKCHAIN_RECONCILIATION_MISMATCH(
            "BLOCKCHAIN_409_RECONCILIATION_MISMATCH",
            409,
            "온체인 상태가 로컬 증빙과 일치하지 않아 복구할 수 없습니다."),
    INVALID_CREDENTIAL_INPUT("CREDENTIAL_400_INPUT", 400, "Credential 발급 입력이 올바르지 않습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
