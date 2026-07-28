package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewAssignmentDueAtReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewAssignmentRes;
import java.util.List;

public interface ReviewAssignmentAdminService {

    List<ReviewAssignmentRes> prepareAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            Long judgeId,
            ReviewAssignmentPrepareReq req);

    List<ReviewAssignmentRes> getAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            Long judgeId);

    ReviewAssignmentRes cancelAssignment(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            Long judgeId,
            Long assignmentId);

    ReviewAssignmentRes reassignAssignment(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            Long judgeId,
            Long assignmentId,
            ReviewAssignmentDueAtReq req);

    ReviewAssignmentRes updateDueAt(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            Long judgeId,
            Long assignmentId,
            ReviewAssignmentDueAtReq req);
}
