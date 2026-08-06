package com.api.trekkey.domain.invitation.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSignUpReq(
        @NotBlank(message = "초대 토큰은 반드시 입력해야합니다")
        String inviteToken,

        @NotBlank(message = "이름은 반드시 입력해야합니다")
        String name,

        @NotBlank(message = "이메일은 반드시 입력해야합니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        String email,

        @NotBlank(message = "비밀번호는 반드시 입력해야합니다")
        @Size(min = 10, message = "비밀번호는 10자 이상이어야 합니다")
        String password,

        String department, //소속부서 (선택)

        String position //직책 (선택)
) {
}
