package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSubmitRes;

public interface ReviewSubmissionService {

    ReviewSubmitRes submitReview(
            Long assignmentId,
            ReviewSubmitReq req);
}
