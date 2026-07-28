package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository.ReviewSubmissionScope;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.repository.ReviewScoreItemRepository;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.web.dto.request.ReviewScoreReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewSubmitRes;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewSubmissionServiceImpl
        implements ReviewSubmissionService {

    private static final int MAX_COMMENT_LENGTH = 5000;
    private static final BigDecimal MAX_TOTAL_SCORE =
            new BigDecimal("9999999999.99");

    private final ReviewLinkAuthenticator reviewLinkAuthenticator;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewCriterionRepository reviewCriterionRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewScoreItemRepository reviewScoreItemRepository;
    private final Clock clock;

    @Override
    public ReviewSubmitRes submitReview(
            Long assignmentId,
            ReviewSubmitReq req
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        String rawToken = req == null ? null : req.token();
        ContestJudge judge =
                reviewLinkAuthenticator.authenticate(rawToken, now);

        ReviewSubmissionScope scope = reviewAssignmentRepository
                .findSubmissionScopeByIdAndJudgeId(
                        assignmentId,
                        judge.getId()
                )
                .orElseThrow(this::assignmentNotFound);
        ReviewRound reviewRound = reviewRoundRepository
                .findByIdForShare(scope.getReviewRoundId())
                .orElseThrow(this::assignmentNotFound);
        validateRoundContest(reviewRound, judge);

        List<ReviewCriterion> criteria = reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(
                        reviewRound.getId());
        ReviewRoundEntry entry = reviewRoundEntryRepository
                .findByIdAndReviewRoundIdForShare(
                        scope.getReviewRoundEntryId(),
                        reviewRound.getId()
                )
                .orElseThrow(this::assignmentNotFound);
        ReviewAssignment assignment = reviewAssignmentRepository
                .findByIdAndJudgeIdAndEntryIdForUpdate(
                        assignmentId,
                        judge.getId(),
                        entry.getId()
                )
                .orElseThrow(this::assignmentNotFound);

        Map<Long, BigDecimal> requestedScores =
                toRequestedScores(req);
        Review existingReview = reviewRepository
                .findByAssignmentIdForShare(assignmentId)
                .orElse(null);

        if (assignment.getStatus()
                == ReviewAssignmentStatus.COMPLETED) {
            return handleCompletedRetry(
                    assignment,
                    existingReview,
                    requestedScores,
                    req == null ? null : req.comment()
            );
        }
        if (existingReview != null) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SUBMISSION_STATE_INVALID);
        }

        validateNewSubmission(
                reviewRound,
                entry,
                assignment,
                req,
                now
        );
        List<ReviewCriterion> activeCriteria = criteria.stream()
                .filter(ReviewCriterion::isActive)
                .toList();
        BigDecimal totalScore = validateAndCalculateTotal(
                activeCriteria,
                requestedScores
        );

        Review review = Review.builder()
                .assignment(assignment)
                .totalScore(totalScore)
                .comment(req.comment())
                .submittedAt(now)
                .build();
        List<ReviewScoreItem> scoreItems;
        try {
            review = reviewRepository.saveAndFlush(review);
            Review savedReview = review;
            scoreItems = activeCriteria.stream()
                    .map(criterion -> ReviewScoreItem.builder()
                            .review(savedReview)
                            .reviewCriterion(criterion)
                            .score(normalizeScore(
                                    requestedScores.get(
                                            criterion.getId())))
                            .build())
                    .toList();
            scoreItems = reviewScoreItemRepository
                    .saveAllAndFlush(scoreItems);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SUBMISSION_DUPLICATED);
        }

        if (!assignment.complete(now)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SUBMISSION_STATE_INVALID);
        }
        return ReviewSubmitRes.of(review, scoreItems);
    }

    private void validateRoundContest(
            ReviewRound reviewRound,
            ContestJudge judge
    ) {
        if (!reviewRound.getContest().getId()
                .equals(judge.getContest().getId())) {
            throw assignmentNotFound();
        }
    }

    private void validateNewSubmission(
            ReviewRound reviewRound,
            ReviewRoundEntry entry,
            ReviewAssignment assignment,
            ReviewSubmitReq req,
            LocalDateTime now
    ) {
        if (entry.getStatus()
                != ReviewRoundEntryStatus.IN_REVIEW
                || assignment.getStatus()
                != ReviewAssignmentStatus.ASSIGNED) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SUBMISSION_NOT_ALLOWED);
        }
        if (!reviewRound.isOpenAt(now)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SUBMISSION_NOT_OPEN);
        }
        if (!assignment.isAvailableAt(now)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SUBMISSION_DEADLINE_EXPIRED);
        }
        if (req == null
                || (req.comment() != null
                && req.comment().length() > MAX_COMMENT_LENGTH)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_COMMENT_TOO_LONG);
        }
    }

    private BigDecimal validateAndCalculateTotal(
            List<ReviewCriterion> activeCriteria,
            Map<Long, BigDecimal> requestedScores
    ) {
        if (activeCriteria.isEmpty()
                || activeCriteria.size() != requestedScores.size()
                || !requestedScores.keySet().equals(
                activeCriteria.stream()
                        .map(ReviewCriterion::getId)
                        .collect(java.util.stream.Collectors.toSet()))) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SCORE_CRITERIA_MISMATCH);
        }

        BigDecimal totalScore = BigDecimal.ZERO.setScale(2);
        for (ReviewCriterion criterion : activeCriteria) {
            BigDecimal score = requestedScores.get(criterion.getId());
            if (!isValidScore(score, criterion.getMaxScore())) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_SCORE_OUT_OF_RANGE);
            }
            totalScore = totalScore.add(normalizeScore(score));
        }
        if (totalScore.compareTo(MAX_TOTAL_SCORE) > 0) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SCORE_OUT_OF_RANGE);
        }
        return totalScore;
    }

    private boolean isValidScore(
            BigDecimal score,
            int maxScore
    ) {
        return score != null
                && score.scale() <= 2
                && score.compareTo(BigDecimal.ZERO) >= 0
                && score.compareTo(BigDecimal.valueOf(maxScore)) <= 0;
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        return score.setScale(2, RoundingMode.UNNECESSARY);
    }

    private Map<Long, BigDecimal> toRequestedScores(
            ReviewSubmitReq req
    ) {
        if (req == null
                || req.scores() == null
                || req.scores().isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SCORE_CRITERIA_MISMATCH);
        }
        Map<Long, BigDecimal> requestedScores = new HashMap<>();
        for (ReviewScoreReq scoreReq : req.scores()) {
            if (scoreReq == null || scoreReq.criterionId() == null) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_SCORE_CRITERIA_MISMATCH);
            }
            if (scoreReq.score() == null) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_SCORE_OUT_OF_RANGE);
            }
            if (requestedScores.putIfAbsent(
                    scoreReq.criterionId(),
                    scoreReq.score()
            ) != null) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_SCORE_CRITERIA_MISMATCH);
            }
        }
        return requestedScores;
    }

    private ReviewSubmitRes handleCompletedRetry(
            ReviewAssignment assignment,
            Review existingReview,
            Map<Long, BigDecimal> requestedScores,
            String requestedComment
    ) {
        if (existingReview == null) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_SUBMISSION_STATE_INVALID);
        }
        List<ReviewScoreItem> existingItems =
                reviewScoreItemRepository
                        .findAllForShareByReviewIdOrderByCriterionIdAsc(
                                existingReview.getId());
        if (!hasSameSubmission(
                existingReview,
                existingItems,
                requestedScores,
                requestedComment
        )) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ALREADY_SUBMITTED);
        }
        return ReviewSubmitRes.of(existingReview, existingItems);
    }

    private boolean hasSameSubmission(
            Review review,
            List<ReviewScoreItem> scoreItems,
            Map<Long, BigDecimal> requestedScores,
            String requestedComment
    ) {
        if (!Objects.equals(review.getComment(), requestedComment)
                || scoreItems.size() != requestedScores.size()) {
            return false;
        }
        for (ReviewScoreItem item : scoreItems) {
            BigDecimal requestedScore = requestedScores.get(
                    item.getReviewCriterion().getId());
            if (requestedScore == null
                    || item.getScore().compareTo(
                    requestedScore) != 0) {
                return false;
            }
        }
        return true;
    }

    private CustomException assignmentNotFound() {
        return new CustomException(
                ReviewErrorResponseCode
                        .REVIEW_ASSIGNMENT_NOT_FOUND);
    }
}
