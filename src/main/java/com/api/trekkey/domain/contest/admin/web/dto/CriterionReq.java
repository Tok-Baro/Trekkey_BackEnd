package com.api.trekkey.domain.contest.admin.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriterionReq(
        // 수정 시 기존 기준 매칭용 (신규면 null)
        Long id,

        // 내부 기준 코드 — 미입력 시 서버가 생성
        @Size(max = 60, message = "기준 코드는 60자 이하여야 합니다")
        @Pattern(
                regexp = "^\\s*(?:[A-Za-z0-9][A-Za-z0-9_-]*)?\\s*$",
                message = "기준 코드는 영문, 숫자, 하이픈, 밑줄만 사용할 수 있습니다")
        String code,

        @NotBlank(message = "평가 기준명은 반드시 입력해야합니다")
        @Size(max = 60, message = "평가 기준명은 60자 이하여야 합니다")
        String label,

        // 기존 0점 기준의 조회-수정 호환은 허용하되, 신규 기준은 서비스에서 1점 이상으로 제한한다.
        @NotNull(message = "배점은 반드시 입력해야합니다")
        @Min(value = 0, message = "배점은 0 이상이어야 합니다")
        Integer maxScore,

        // 미입력 시 요청 순서대로 부여
        @Min(value = 1, message = "평가 기준 순서는 1 이상이어야 합니다")
        Integer sortOrder
) {
}
