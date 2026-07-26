package com.api.trekkey.domain.review.publicapi.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ReviewSubmitReq(
        @Size(max = 2000, message = "심사 의견은 2000자 이하로 입력해주세요.")
        String comment,

        @NotEmpty(message = "평가 항목 점수를 입력해주세요.")
        @Valid
        List<ScoreReq> scores
) {
    public record ScoreReq(
            @NotNull(message = "평가 기준을 선택해주세요.")
            Long criterionId,

            @NotNull(message = "점수를 입력해주세요.")
            @DecimalMin(value = "0", message = "점수는 0 이상이어야 합니다.")
            BigDecimal score
    ) {
    }
}
