package com.api.trekkey.domain.review.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReviewSubmitReq(
        // 원문이 validation error에 노출되지 않도록 인증기에서 형식을 검증한다.
        String token,

        @NotEmpty(message = "평가 기준별 점수를 입력해야 합니다.")
        @Valid
        List<@NotNull(message = "평가 점수 항목이 필요합니다.")
                ReviewScoreReq> scores,

        // 의견 원문 노출을 피하기 위해 길이는 서비스의 도메인 오류로 검증한다.
        String comment
) {
    @Override
    public String toString() {
        return "ReviewSubmitReq[token=***, scores="
                + (scores == null ? null : scores.size())
                + ", comment=***]";
    }
}
