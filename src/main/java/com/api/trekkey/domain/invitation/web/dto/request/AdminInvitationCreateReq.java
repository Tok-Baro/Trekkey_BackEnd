package com.api.trekkey.domain.invitation.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminInvitationCreateReq(
        @NotBlank(message = "초대할 이메일은 반드시 입력해야합니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        String email
) {
}
