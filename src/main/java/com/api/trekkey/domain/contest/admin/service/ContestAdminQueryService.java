package com.api.trekkey.domain.contest.admin.service;

import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSummaryRes;
import com.api.trekkey.global.response.PageRes;

public interface ContestAdminQueryService {

    // 관리자 콘솔 대회 목록 — 관리자 소속 조직의 대회만 조회한다.
    PageRes<ContestAdminSummaryRes> getContests(Long userId, ContestAdminSearchCond cond);
}
