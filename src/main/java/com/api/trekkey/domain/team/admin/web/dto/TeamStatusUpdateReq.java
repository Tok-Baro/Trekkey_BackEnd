package com.api.trekkey.domain.team.admin.web.dto;

import com.api.trekkey.domain.team.entity.TeamStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeamStatusUpdateReq(
        @NotNull(message = "변경할 상태를 선택해주세요.")
        TeamStatus status,

        @Size(max = 500, message = "보완 요청 사유는 500자 이하여야 합니다.")
        String revisionReason
) {
    public TeamStatusUpdateReq(TeamStatus status) {
        this(status, null);
    }
}
