package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAccessRes;

public interface ReviewAccessService {

    ReviewAccessRes verifyAccess(ReviewAccessReq req);
}
