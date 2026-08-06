package com.api.trekkey.domain.review.admin.web.dto.response;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import com.api.trekkey.domain.submission.entity.Submission;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewRecordRes(
        Long reviewId,
        Long assignmentId,
        Long reviewRoundId,
        Long reviewRoundEntryId,
        Long judgeId,
        String judgeName,
        String judgeRoleLabel,
        String submissionPublicId,
        String submissionTitle,
        String teamName,
        BigDecimal totalScore,
        String comment,
        LocalDateTime submittedAt,
        List<ReviewRecordScoreRes> scores
) {
    public static ReviewRecordRes from(
            Review review,
            List<ReviewScoreItem> scoreItems) {
        ReviewAssignment assignment = review.getAssignment();
        ReviewRoundEntry entry = assignment.getReviewRoundEntry();
        ContestJudge judge = assignment.getContestJudge();
        Submission submission = entry.getSubmission();

        return new ReviewRecordRes(
                review.getId(),
                assignment.getId(),
                entry.getReviewRound().getId(),
                entry.getId(),
                judge.getId(),
                judge.getName(),
                judge.getRoleLabel(),
                submission.getPublicId(),
                submission.getTitle(),
                submission.getTeam().getName(),
                review.getTotalScore(),
                review.getComment(),
                review.getSubmittedAt(),
                scoreItems.stream()
                        .map(ReviewRecordScoreRes::from)
                        .toList());
    }
}
