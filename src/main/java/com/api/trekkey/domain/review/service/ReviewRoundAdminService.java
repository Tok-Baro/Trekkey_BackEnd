package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.review.web.dto.request.ReviewRoundSaveReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundRes;
import java.util.List;

public interface ReviewRoundAdminService {

    ReviewRoundRes createRound(
            Long adminUserId,
            String contestPublicId,
            ReviewRoundSaveReq req
    );

    List<ReviewRoundRes> getRounds(
            Long adminUserId,
            String contestPublicId
    );

    ReviewRoundRes getRound(
            Long adminUserId,
            String contestPublicId,
            Long roundId
    );

    ReviewRoundRes updateRound(
            Long adminUserId,
            String contestPublicId,
            Long roundId,
            ReviewRoundSaveReq req
    );

    ReviewRoundRes openRound(
            Long adminUserId,
            String contestPublicId,
            Long roundId
    );
}
