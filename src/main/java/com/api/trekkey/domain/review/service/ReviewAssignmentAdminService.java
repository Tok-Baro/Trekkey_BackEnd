package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.review.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAssignmentRes;
import java.util.List;

public interface ReviewAssignmentAdminService {

    List<ReviewAssignmentRes> prepareAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId,
            Long judgeId,
            ReviewAssignmentPrepareReq req);

    List<ReviewAssignmentRes> getAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId,
            Long judgeId);
}
