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
            "참가 인원이 대회의 참가 방식과 맞지 않습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
