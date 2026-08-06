package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRecordRes;
import java.util.List;

public interface ReviewRecordAdminService {

    List<ReviewRecordRes> getReviews(
            Long adminUserId,
            String contestPublicId,
            Long roundId);
}
