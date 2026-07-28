package com.api.trekkey.domain.contest.admin.service;

import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSummaryRes;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.global.response.PageRes;

public interface ContestAdminQueryService {

    // 관리자 콘솔 대회 목록 — 관리자 소속 조직의 대회만 조회한다.
    PageRes<ContestAdminSummaryRes> getContests(Long userId, ContestAdminSearchCond cond);

    // 관리자 콘솔 대회 상세 — 준비 중인 대회를 포함해 전체 단계 설정을 조회한다.
    ContestDetailRes getContest(Long userId, String publicId);
}
