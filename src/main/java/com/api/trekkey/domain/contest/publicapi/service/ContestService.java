package com.api.trekkey.domain.contest.publicapi.service;

import com.api.trekkey.domain.contest.publicapi.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import java.util.List;

public interface ContestService {
    List<ContestSearchRes> searchContests(Long userId, String keyword, ContestSearchStatus status);

    ContestDetailRes getContestDetail(Long userId, String publicId);

    // 좋아요 토글 — 이미 눌렀으면 취소, 아니면 등록. 현재 좋아요 수를 반환한다.
    long toggleLike(Long userId, String contestPublicId);
}
