package com.api.trekkey.domain.award.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AwardErrorResponseCode implements BaseResponseCode {
    AWARD_NOT_FOUND(
            "AWARD_NOT_FOUND",
            404,
            "수상 정보를 찾을 수 없습니다."),
    AWARD_ROUND_NOT_FINALIZED(
            "AWARD_ROUND_NOT_FINALIZED",
            409,
            "확정된 라운드가 있어야 수상 후보를 산출할 수 있습니다."),
    AWARD_FINAL_ROUND_REQUIRED(
            "AWARD_FINAL_ROUND_REQUIRED",
            409,
            "대회의 마지막 심사 라운드에서만 수상 후보를 산출할 수 있습니다."),
    AWARD_NO_PASSED_ENTRY(
            "AWARD_NO_PASSED_ENTRY",
            400,
            "수상 후보로 산출할 통과작이 없습니다."),
    AWARD_ALREADY_CONFIRMED(
            "AWARD_ALREADY_CONFIRMED",
            409,
            "이미 확정된 수상 결과가 있어 재산출할 수 없습니다."),
    AWARD_NO_CANDIDATE(
            "AWARD_NO_CANDIDATE",
            400,
            "확정할 수상 후보가 없습니다."),
    AWARD_CANDIDATES_STALE(
            "AWARD_CANDIDATES_STALE",
            409,
            "대회 설정 또는 최종 심사 결과가 후보 산출 이후 변경되었습니다. 수상 후보를 다시 산출해 주세요."),
    AWARD_HELD_EXISTS(
            "AWARD_HELD_EXISTS",
            409,
            "보류된 수상 후보가 있습니다. 모든 후보를 확정 대기 상태로 변경한 뒤 확정해 주세요."),
    AWARD_CANDIDATE_UPDATE_NOT_ALLOWED(
            "AWARD_CANDIDATE_UPDATE_NOT_ALLOWED",
            409,
            "확정된 수상 결과는 후보 편집으로 변경할 수 없습니다."),
    AWARD_CUSTOM_PRIZE_REQUIRED(
            "AWARD_CUSTOM_PRIZE_REQUIRED",
            400,
            "직접 입력 상격의 이름을 입력해주세요."),
    AWARD_CANDIDATE_STATUS_INVALID(
            "AWARD_CANDIDATE_STATUS_INVALID",
            400,
            "수상 후보 상태는 후보 또는 보류만 선택할 수 있습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
