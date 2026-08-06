package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundFinalizeReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundFinalizeRes;

public interface ReviewRoundFinalizationAdminService {

    ReviewRoundFinalizeRes finalizeRound(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            ReviewRoundFinalizeReq req
    );
}
