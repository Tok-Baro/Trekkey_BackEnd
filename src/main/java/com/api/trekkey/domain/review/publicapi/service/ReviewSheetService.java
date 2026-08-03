package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSheetRes;

public interface ReviewSheetService {

    ReviewSheetRes getReviewSheet(ReviewAccessReq req);
}
