package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.review.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewSubmitRes;

public interface ReviewSubmissionService {

    ReviewSubmitRes submitReview(
            Long assignmentId,
            ReviewSubmitReq req);
}
