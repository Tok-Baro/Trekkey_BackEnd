package com.api.trekkey.domain.contest.publicapi.service;

import com.api.trekkey.domain.contest.publicapi.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import java.util.List;

public interface ContestService {
    List<ContestSearchRes> searchContests(Long userId, String keyword, ContestSearchStatus status);

    ContestDetailRes getContestDetail(String publicId);
}
