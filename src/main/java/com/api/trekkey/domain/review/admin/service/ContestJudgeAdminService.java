package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.review.admin.web.dto.request.ContestJudgeCreateReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewLinkIssueReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ContestJudgeRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewLinkIssueRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewJudgeProgressRes;
import java.util.List;

public interface ContestJudgeAdminService {

    ContestJudgeRes createJudge(
            Long adminUserId,
            String contestPublicId,
            ContestJudgeCreateReq req
    );

    List<ContestJudgeRes> getJudges(Long adminUserId, String contestPublicId);

    List<ReviewJudgeProgressRes> getJudgeProgress(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId);

    void deleteJudge(
            Long adminUserId,
            String contestPublicId,
            Long judgeId);

    ReviewLinkIssueRes issueReviewLink(
            Long adminUserId,
            String contestPublicId,
            Long judgeId,
            ReviewLinkIssueReq req
    );

    void revokeReviewLink(Long adminUserId, String contestPublicId, Long judgeId);
}
