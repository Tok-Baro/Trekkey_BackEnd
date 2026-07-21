package com.api.trekkey.domain.contest.service;

import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.web.dto.ContestSearchStatus;
import java.util.List;

public interface ContestService {
    List<ContestSearchRes> searchContests(Long userId, String keyword, ContestSearchStatus status);

    // 공개 공고 상세 — 단계와 평가 기준을 포함해 조회한다. (비로그인 접근 허용)
    ContestDetailRes getContestDetail(String publicId);
}
