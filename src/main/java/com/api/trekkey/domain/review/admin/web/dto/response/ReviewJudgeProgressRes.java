package com.api.trekkey.domain.review.admin.web.dto.response;

import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;

public record ReviewJudgeProgressRes(
        Long judgeId,
        String judgeName,
        long assignedCount,
        long completedCount,
        long pendingCount,
        long overdueCount
) {
    public static ReviewJudgeProgressRes from(
            ReviewAssignmentRepository.JudgeProgressProjection projection
    ) {
        return new ReviewJudgeProgressRes(
                projection.getJudgeId(),
                projection.getJudgeName(),
                projection.getAssignedCount(),
                projection.getCompletedCount(),
                projection.getPendingCount(),
                projection.getOverdueCount()
        );
    }
}
