package com.api.trekkey.domain.invitation.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AdminInvitationErrorResponseCode implements BaseResponseCode {
    INVITATION_INVALID("INVITATION_INVALID", 400, "유효하지 않은 초대입니다"),
    INVITATION_EXPIRED("INVITATION_EXPIRED", 400, "만료된 초대입니다"),
    INVITATION_ALREADY_USED("INVITATION_ALREADY_USED", 409, "이미 사용된 초대입니다"),
    INVITATION_DUPLICATED("INVITATION_DUPLICATED", 409, "이미 발급된 초대가 있습니다"),
    APPROVAL_TARGET_INVALID("APPROVAL_TARGET_INVALID", 400, "승인 대상 상태가 아닙니다");

    private final String code;
    private final int httpStatus;
    private final String message;
}
