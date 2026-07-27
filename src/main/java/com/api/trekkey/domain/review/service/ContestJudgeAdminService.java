package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.review.web.dto.request.ContestJudgeCreateReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewLinkIssueReq;
import com.api.trekkey.domain.review.web.dto.response.ContestJudgeRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewLinkIssueRes;
import java.util.List;

public interface ContestJudgeAdminService {

    ContestJudgeRes createJudge(
            Long adminUserId,
            String contestPublicId,
            ContestJudgeCreateReq req
    );

    List<ContestJudgeRes> getJudges(Long adminUserId, String contestPublicId);

    ReviewLinkIssueRes issueReviewLink(
            Long adminUserId,
            String contestPublicId,
            Long judgeId,
            ReviewLinkIssueReq req
    );

    void revokeReviewLink(Long adminUserId, String contestPublicId, Long judgeId);
}
