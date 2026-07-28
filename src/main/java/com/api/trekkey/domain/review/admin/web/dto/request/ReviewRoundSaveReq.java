package com.api.trekkey.domain.review.admin.web.dto.request;

import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewRoundSaveReq(
        @NotNull(message = "심사 라운드 순서는 반드시 입력해야 합니다.")
        @Min(value = 1, message = "심사 라운드 순서는 1 이상이어야 합니다.")
        Integer roundNo,

        @NotBlank(message = "심사 라운드명은 반드시 입력해야 합니다.")
        @Size(max = 100, message = "심사 라운드명은 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "심사 시작 시각은 반드시 입력해야 합니다.")
        LocalDateTime startsAt,

        @NotNull(message = "심사 종료 시각은 반드시 입력해야 합니다.")
        LocalDateTime endsAt,

        @NotNull(message = "심사 대상 선정 방식을 반드시 입력해야 합니다.")
        ReviewRoundTargetType targetType,

        @NotNull(message = "심사 판정 방식을 반드시 입력해야 합니다.")
        ReviewRoundDecisionRule decisionRule,

        @Positive(message = "선정 팀 수는 1 이상이어야 합니다.")
        Integer selectCount,

        @DecimalMin(value = "0", message = "최소 선정 점수는 0점 이상이어야 합니다.")
        @Digits(
                integer = 10,
                fraction = 2,
                message = "최소 선정 점수는 소수점 둘째 자리까지만 입력할 수 있습니다.")
        BigDecimal minScore,

        @Valid
        List<@NotNull(message = "평가 기준 항목이 필요합니다.")
                ReviewRoundCriterionReq> criteria
) {
}
