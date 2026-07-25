package com.api.trekkey.domain.review.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JudgeCreateReq(
        @NotBlank(message = "심사위원 이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "역할명을 입력해주세요.")
        @Size(max = 50, message = "역할명은 50자 이하로 입력해주세요.")
        String roleLabel
) {
}
