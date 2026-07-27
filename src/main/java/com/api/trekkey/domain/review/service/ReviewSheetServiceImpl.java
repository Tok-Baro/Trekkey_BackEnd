package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetAssignmentRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetCriterionRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetStageRes;
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

        if (judge.getContest().getStatus() != ContestStatus.REVIEWING) {
            return ReviewSheetRes.of(judge, List.of());
        }

        List<ReviewAssignment> visibleAssignments =
                reviewAssignmentRepository
                        .findAllWithDetailsByJudgeId(judge.getId())
                        .stream()
                        .filter(assignment -> isVisible(assignment, now))
                        .toList();
        if (visibleAssignments.isEmpty()) {
            return ReviewSheetRes.of(judge, List.of());
        }

        List<Long> stageIds = visibleAssignments.stream()
                .map(assignment -> assignment
                        .getReviewRoundEntry()
                        .getReviewStage()
                        .getId())
                .distinct()
                .toList();
        Map<Long, List<ReviewCriterion>> criteriaByStageId =
                groupActiveCriteria(reviewCriterionRepository
                        .findAllByContestStageIdInOrderBySortOrderAsc(
                                stageIds));

        Map<Long, List<ReviewAssignment>> assignmentsByStageId =
                new LinkedHashMap<>();
        for (ReviewAssignment assignment : visibleAssignments) {
            Long stageId = assignment
                    .getReviewRoundEntry()
                    .getReviewStage()
                    .getId();
            assignmentsByStageId
                    .computeIfAbsent(stageId, key -> new ArrayList<>())
                    .add(assignment);
        }

        List<ReviewSheetStageRes> stages = new ArrayList<>();
        for (List<ReviewAssignment> stageAssignments
                : assignmentsByStageId.values()) {
            ContestStage stage = stageAssignments.getFirst()
                    .getReviewRoundEntry()
                    .getReviewStage();
            List<ReviewSheetCriterionRes> criteria =
                    criteriaByStageId
                            .getOrDefault(stage.getId(), List.of())
                            .stream()
                            .map(ReviewSheetCriterionRes::from)
                            .toList();
            List<ReviewSheetAssignmentRes> assignments =
                    stageAssignments.stream()
                            .map(ReviewSheetAssignmentRes::from)
                            .toList();
            stages.add(new ReviewSheetStageRes(
                    stage.getId(),
                    stage.getName(),
                    stage.getStartsAt(),
                    stage.getEndsAt(),
                    criteria,
                    assignments
            ));
        }

        return ReviewSheetRes.of(judge, stages);
    }

    private boolean isVisible(
            ReviewAssignment assignment,
            LocalDateTime now
    ) {
        ContestStage stage = assignment
                .getReviewRoundEntry()
                .getReviewStage();
        if (!stage.isOpenAt(now)
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
                                criterion.getContestStage().getId())
                        .thenComparingInt(ReviewCriterion::getSortOrder)
                        .thenComparing(
                                ReviewCriterion::getId,
                                Comparator.nullsLast(Long::compareTo)
                        ))
                .forEach(criterion -> grouped
                        .computeIfAbsent(
                                criterion.getContestStage().getId(),
                                key -> new ArrayList<>()
                        )
                        .add(criterion));
        return grouped;
    }
}
