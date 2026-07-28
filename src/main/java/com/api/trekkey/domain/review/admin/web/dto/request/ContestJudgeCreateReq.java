package com.api.trekkey.domain.review.admin.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ContestJudgeCreateReq(
        @Positive(message = "연결 사용자 ID는 1 이상이어야 합니다")
        Long userId,

        @NotBlank(message = "심사위원 이름은 반드시 입력해야 합니다")
        @Size(max = 100, message = "심사위원 이름은 100자 이하여야 합니다")
        String name,

        @NotBlank(message = "심사위원 역할은 반드시 입력해야 합니다")
        @Size(max = 100, message = "심사위원 역할은 100자 이하여야 합니다")
        String roleLabel
) {
}
