package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSheetAssignmentRes;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSheetCriterionRes;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSheetRes;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSheetRoundRes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewSheetServiceImpl implements ReviewSheetService {

    private final ReviewLinkAuthenticator reviewLinkAuthenticator;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final ReviewCriterionRepository reviewCriterionRepository;
    private final Clock clock;

    @Override
    public ReviewSheetRes getReviewSheet(ReviewAccessReq req) {
        LocalDateTime now = LocalDateTime.now(clock);
        String rawToken = req == null ? null : req.token();
        ContestJudge judge =
                reviewLinkAuthenticator.authenticate(rawToken, now);

        List<ReviewAssignment> visibleAssignments =
                reviewAssignmentRepository
                        .findAllWithDetailsByJudgeId(judge.getId())
                        .stream()
                        .filter(assignment -> isVisible(assignment, now))
                        .toList();
        if (visibleAssignments.isEmpty()) {
            return ReviewSheetRes.of(judge, List.of());
        }

        List<Long> roundIds = visibleAssignments.stream()
                .map(assignment -> assignment
                        .getReviewRoundEntry()
                        .getReviewRound()
                        .getId())
                .distinct()
                .toList();
        Map<Long, List<ReviewCriterion>> criteriaByRoundId =
                groupActiveCriteria(reviewCriterionRepository
                        .findAllByReviewRoundIdInOrderBySortOrderAsc(
                                roundIds));

        Map<Long, List<ReviewAssignment>> assignmentsByRoundId =
                new LinkedHashMap<>();
        for (ReviewAssignment assignment : visibleAssignments) {
            Long roundId = assignment
                    .getReviewRoundEntry()
                    .getReviewRound()
                    .getId();
            assignmentsByRoundId
                    .computeIfAbsent(roundId, key -> new ArrayList<>())
                    .add(assignment);
        }

        List<ReviewSheetRoundRes> rounds = new ArrayList<>();
        for (List<ReviewAssignment> roundAssignments
                : assignmentsByRoundId.values()) {
            ReviewRound round = roundAssignments.getFirst()
                    .getReviewRoundEntry()
                    .getReviewRound();
            List<ReviewSheetCriterionRes> criteria =
                    criteriaByRoundId
                            .getOrDefault(round.getId(), List.of())
                            .stream()
                            .map(ReviewSheetCriterionRes::from)
                            .toList();
            List<ReviewSheetAssignmentRes> assignments =
                    roundAssignments.stream()
                            .map(ReviewSheetAssignmentRes::from)
                            .toList();
            rounds.add(new ReviewSheetRoundRes(
                    round.getId(),
                    round.getRoundNo(),
                    round.getName(),
                    round.getStartsAt(),
                    round.getEndsAt(),
                    criteria,
                    assignments
            ));
        }

        return ReviewSheetRes.of(judge, rounds);
    }

    private boolean isVisible(
            ReviewAssignment assignment,
            LocalDateTime now
    ) {
        ReviewRound round = assignment
                .getReviewRoundEntry()
                .getReviewRound();
        if (!round.isOpenAt(now)
                || assignment.getStatus()
                == ReviewAssignmentStatus.CANCELED) {
            return false;
        }
        return assignment.getStatus()
                == ReviewAssignmentStatus.COMPLETED
                || assignment.isAvailableAt(now);
    }

    private Map<Long, List<ReviewCriterion>> groupActiveCriteria(
            List<ReviewCriterion> criteria
    ) {
        Map<Long, List<ReviewCriterion>> grouped = new LinkedHashMap<>();
        criteria.stream()
                .filter(ReviewCriterion::isActive)
                .sorted(Comparator
                        .comparing((ReviewCriterion criterion) ->
                                criterion.getReviewRound().getRoundNo())
                        .thenComparingInt(ReviewCriterion::getSortOrder)
                        .thenComparing(
                                ReviewCriterion::getId,
                                Comparator.nullsLast(Long::compareTo)
                        ))
                .forEach(criterion -> grouped
                        .computeIfAbsent(
                                criterion.getReviewRound().getId(),
                                key -> new ArrayList<>()
                        )
                        .add(criterion));
        return grouped;
    }
}
