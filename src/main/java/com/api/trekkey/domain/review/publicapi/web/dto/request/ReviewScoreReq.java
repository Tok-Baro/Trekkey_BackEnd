package com.api.trekkey.domain.review.publicapi.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ReviewScoreReq(
        @NotNull(message = "평가 기준 ID가 필요합니다.")
        @Positive(message = "평가 기준 ID는 양수여야 합니다.")
        Long criterionId,

        @NotNull(message = "평가 점수가 필요합니다.")
        @DecimalMin(
                value = "0",
                message = "평가 점수는 0 이상이어야 합니다.")
        @Digits(
                integer = 10,
                fraction = 2,
                message = "평가 점수는 소수점 둘째 자리까지만 입력할 수 있습니다.")
        BigDecimal score
) {
}
