package com.api.trekkey.domain.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UserSignUpReq {

    @NotNull(message = "학교선택은 필수입니다")
    private Long organizationId;

    @NotBlank(message = "이름은 반드시 입력해야합니다")
    private String name;

    @NotBlank(message = "이메일은 반드시 입력해야합니다")
    @Email
    private String email;

    @NotBlank(message = "비밀번호는 반드시 입력해양합니다")
    private String password;

    private String studentId;
    private String major;
}
