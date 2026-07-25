package com.api.trekkey.domain.team.publicapi.service;

import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamRes;
import java.util.List;

public interface TeamApplicationService {

    void createApplication(Long userId, String contestPublicId, TeamApplicationCreateReq request);

    // 내가 대표자인 참가 신청 목록 — 참가자 포털 내 신청 화면
    List<TeamRes> getMyApplications(Long userId);

    // 참가 신청 수정 — 대표자 본인만, 명단 확정 전까지. 보완요청 상태면 검토중으로 자동 전환한다.
    TeamRes updateApplication(Long userId, String teamPublicId, TeamApplicationUpdateReq request);
}
