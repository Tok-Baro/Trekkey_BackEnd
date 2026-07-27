package com.api.trekkey.domain.team.admin.service;

import com.api.trekkey.domain.team.admin.web.dto.TeamAdminListRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamStatusUpdateReq;
import com.api.trekkey.domain.team.entity.TeamStatus;

public interface TeamAdminService {

    // 대회별 참가 신청 목록 — 상태 필터와 상태별 카운트 요약 포함. 같은 조직 관리자만 조회할 수 있다.
    TeamAdminListRes getTeams(Long adminUserId, String contestPublicId, TeamStatus status);

    // 신청 상태 변경 (승인/보완요청/반려)
    TeamAdminRes changeStatus(Long adminUserId, String teamPublicId, TeamStatusUpdateReq request);

    // 팀원 명단 확정 — 승인된 팀만 가능하며, 확정 이후 신청 수정이 잠긴다 (erd-mvp §5)
    TeamAdminRes finalizeParticipation(Long adminUserId, String teamPublicId);
}
