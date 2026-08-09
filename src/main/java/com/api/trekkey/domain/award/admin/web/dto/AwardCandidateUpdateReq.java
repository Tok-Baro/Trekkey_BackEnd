package com.api.trekkey.domain.award.admin.web.dto;

import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.entity.AwardType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AwardCandidateUpdateReq(
        @NotNull(message = "상격 유형을 선택해주세요.")
        AwardType awardType,

        @Size(max = 50, message = "사용자 정의 상격은 50자 이하여야 합니다.")
        String customPrize,

        @NotNull(message = "수상 후보 상태를 선택해주세요.")
        AwardStatus status
) {
}
