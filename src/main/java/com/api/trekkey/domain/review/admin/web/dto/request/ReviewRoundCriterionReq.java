package com.api.trekkey.domain.review.admin.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewRoundCriterionReq(
        @Positive(message = "평가 기준 ID는 1 이상이어야 합니다.")
        Long id,

        @NotBlank(message = "평가 기준 코드는 반드시 입력해야 합니다.")
        @Size(max = 60, message = "평가 기준 코드는 60자 이하여야 합니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$",
                message = "평가 기준 코드는 영문, 숫자, 하이픈, 밑줄만 사용할 수 있습니다.")
        String code,

        @NotBlank(message = "평가 기준명은 반드시 입력해야 합니다.")
        @Size(max = 60, message = "평가 기준명은 60자 이하여야 합니다.")
        String label,

        @NotNull(message = "평가 기준 배점은 반드시 입력해야 합니다.")
        @Min(value = 1, message = "평가 기준 배점은 1점 이상이어야 합니다.")
        Integer maxScore,

        @NotNull(message = "평가 기준 순서는 반드시 입력해야 합니다.")
        @Min(value = 1, message = "평가 기준 순서는 1 이상이어야 합니다.")
        Integer sortOrder
) {
}
