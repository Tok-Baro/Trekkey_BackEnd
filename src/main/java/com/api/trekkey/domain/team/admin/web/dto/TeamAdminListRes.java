package com.api.trekkey.domain.team.admin.web.dto;

import com.api.trekkey.domain.team.entity.TeamStatus;
import java.util.List;
import java.util.Map;

public record TeamAdminListRes(
        List<TeamAdminRes> content,
        // 상태별 신청 수 — 관리자 화면 필터 요약 (전체 기준, 필터와 무관)
        Map<TeamStatus, Long> statusCounts,
        long total
) {
}
