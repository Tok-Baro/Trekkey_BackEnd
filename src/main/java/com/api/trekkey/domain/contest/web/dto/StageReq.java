package com.api.trekkey.domain.contest.web.dto;

import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageTargetType;
import com.api.trekkey.domain.contest.entity.StageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StageReq(
        // 수정 시 기존 단계 매칭용 (신규면 null)
        Long id,

        @NotBlank(message = "단계명은 반드시 입력해야합니다")
        @Size(max = 100, message = "단계명은 100자 이하여야 합니다")
        String name,

        @NotNull(message = "단계 유형은 반드시 선택해야합니다")
        StageType stageType,

        @NotNull(message = "단계 순서는 반드시 입력해야합니다")
        @Min(value = 1, message = "단계 순서는 1 이상이어야 합니다")
        Integer sequenceNo,

        @NotNull(message = "단계 상태는 반드시 선택해야합니다")
        StageStatus status,

        LocalDateTime startsAt,

        LocalDateTime endsAt,

        // 평가 대상 선정 방식 (심사 단계용)
        StageTargetType targetType,

        // 통과 방식 (심사 단계용)
        StagePassRule passRule,

        @Min(value = 0, message = "통과 팀 수는 0 이상이어야 합니다")
        Integer passCount,

        @DecimalMin(value = "0", message = "기준 점수는 0 이상이어야 합니다")
        BigDecimal minScore,

        // 심사 단계의 점수 기준
        @Valid
        List<CriterionReq> criteria
) {
}
