package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewAccessRes;

public interface ReviewAccessService {

    ReviewAccessRes verifyAccess(ReviewAccessReq req);
}
