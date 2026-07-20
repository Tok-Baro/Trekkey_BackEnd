package com.api.trekkey.domain.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserSignInReq {

    @NotBlank(message = "이메일은 반드시 입력해야합니다")
    @Email
    private String email;

    @NotBlank(message = "비밀번호는 반드시 입력해야합니다")
    private String password;
}
