package com.api.trekkey.domain.team.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TeamErrorResponseCode implements BaseResponseCode {
    TEAM_APPLICATION_NOT_OPEN(
            "TEAM_APPLICATION_NOT_OPEN",
            409,
            "현재 참가 신청이 열려 있지 않은 대회입니다."),
    TEAM_APPLICATION_ALREADY_EXISTS(
            "TEAM_APPLICATION_ALREADY_EXISTS",
            409,
            "이미 해당 대회에 참가 신청했습니다."),
    TEAM_APPLICATION_MEMBER_COUNT_INVALID(
            "TEAM_APPLICATION_MEMBER_COUNT_INVALID",
            400,
            "참가 인원이 대회의 참가 방식과 맞지 않습니다."),
    TEAM_APPLICATION_MEMBER_INVALID(
            "TEAM_APPLICATION_MEMBER_INVALID",
            400,
            "유효하지 않은 팀원이 포함되어 있습니다."),
    TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING(
            "TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING",
            409,
            "이미 해당 대회에 참가 중인 팀원이 포함되어 있습니다."),
    TEAM_NOT_FOUND(
            "TEAM_NOT_FOUND",
            404,
            "신청 정보를 찾을 수 없습니다."),
    TEAM_ALREADY_FINALIZED(
            "TEAM_ALREADY_FINALIZED",
            409,
            "명단이 확정된 팀은 수정할 수 없습니다."),
    TEAM_NOT_APPROVED(
            "TEAM_NOT_APPROVED",
            400,
            "승인된 팀만 명단을 확정할 수 있습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
