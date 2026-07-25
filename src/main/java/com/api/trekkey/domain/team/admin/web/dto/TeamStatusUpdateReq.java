package com.api.trekkey.domain.team.admin.web.dto;

import com.api.trekkey.domain.team.entity.TeamStatus;
import jakarta.validation.constraints.NotNull;

public record TeamStatusUpdateReq(
        @NotNull(message = "변경할 상태를 선택해주세요.")
        TeamStatus status
) {
}
