package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewManualDecisionReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundFinalizeReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundFinalizeRes;
import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewRoundFinalizationAdminServiceImpl
        implements ReviewRoundFinalizationAdminService {

    private static final String TARGET_TYPE_REVIEW_ROUND = "REVIEW_ROUND";
    private static final int MAX_DECISION_REASON_LENGTH = 2000;

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final ReviewRepository reviewRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final Clock clock;

    @Override
    @Transactional
    public ReviewRoundFinalizeRes finalizeRound(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            ReviewRoundFinalizeReq req
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        if (contest.getStatus() == ContestStatus.AWARDED) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_FINALIZATION_NOT_ALLOWED);
        }
        validateRoundOrganization(reviewRoundId, admin);

        ReviewRound round = reviewRoundRepository
                .findByIdForUpdate(reviewRoundId)
                .orElseThrow(this::roundNotFound);
        if (!round.getContest().getId().equals(contest.getId())) {
            throw roundNotFound();
        }
        if (round.getStatus() != ReviewRoundStatus.OPEN) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_FINALIZATION_NOT_ALLOWED);
        }

        List<ReviewRoundEntry> entries = reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(reviewRoundId);
        if (entries.isEmpty()
                || entries.stream().anyMatch(entry ->
                entry.getStatus() != ReviewRoundEntryStatus.IN_REVIEW)) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_RESULT_INVALID);
        }

        List<Long> entryIds = entries.stream()
                .map(ReviewRoundEntry::getId)
                .toList();
        Map<Long, ReviewManualDecisionReq> manualDecisions =
                resolveManualDecisions(round, entries, req);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ReviewAssignment> assignments = reviewAssignmentRepository
                .findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        entryIds);
        List<ReviewAssignment> activeAssignments = assignments.stream()
                .filter(assignment -> assignment.getStatus()
                        != ReviewAssignmentStatus.CANCELED)
                .toList();

        if (round.isManualWithoutReview()) {
            if (!activeAssignments.isEmpty()) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE);
            }
            for (ReviewRoundEntry entry : entries) {
                ReviewManualDecisionReq decision =
                        manualDecisions.get(entry.getId());
                if (decision == null
                        || !entry.finalizeManually(
                        null,
                        decision.rankNo(),
                        decision.status(),
                        admin,
                        decision.reason(),
                        now)) {
                    throw new CustomException(
                            ReviewErrorResponseCode
                                    .REVIEW_ROUND_RESULT_INVALID);
                }
            }
        } else {
            validateCompletedAssignments(entries, activeAssignments);
            Map<Long, Review> reviewsByAssignmentId = reviewRepository
                    .findAllByAssignmentIdIn(activeAssignments.stream()
                            .map(ReviewAssignment::getId)
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(
                            review -> review.getAssignment().getId(),
                            Function.identity()
                    ));
            if (reviewsByAssignmentId.size()
                    != activeAssignments.size()) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE);
            }

            Map<Long, BigDecimal> averageByEntryId =
                    calculateAverageScores(
                            entries,
                            activeAssignments,
                            reviewsByAssignmentId);
            List<RankedEntry> rankedEntries =
                    rankEntries(entries, averageByEntryId);
            for (RankedEntry rankedEntry : rankedEntries) {
                ReviewRoundEntry entry = rankedEntry.entry();
                boolean finalized = round.getDecisionRule()
                        == ReviewRoundDecisionRule.MANUAL
                        ? finalizeManually(
                                entry,
                                rankedEntry,
                                manualDecisions.get(entry.getId()),
                                admin,
                                now)
                        : finalizeByRule(
                                round,
                                entry,
                                rankedEntry,
                                now);
                if (!finalized) {
                    throw new CustomException(
                            ReviewErrorResponseCode
                                    .REVIEW_ROUND_RESULT_INVALID);
                }
            }
        }
        if (!round.finalizeAt(now)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_FINALIZATION_NOT_ALLOWED);
        }

        reviewRoundEntryRepository.flush();
        reviewRoundRepository.flush();
        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.REVIEW_ROUND_FINALIZE,
                TARGET_TYPE_REVIEW_ROUND,
                round.getId(),
                "contestId=" + contest.getId()
                        + ", entryCount=" + entries.size()
                        + ", rule=" + round.getDecisionRule()
        );
        return ReviewRoundFinalizeRes.from(round, entries);
    }

    private void validateCompletedAssignments(
            List<ReviewRoundEntry> entries,
            List<ReviewAssignment> activeAssignments
    ) {
        Set<Long> entryIdsWithAssignment = activeAssignments.stream()
                .map(assignment ->
                        assignment.getReviewRoundEntry().getId())
                .collect(Collectors.toSet());
        if (entries.stream().anyMatch(entry ->
                !entryIdsWithAssignment.contains(entry.getId()))
                || activeAssignments.stream().anyMatch(assignment ->
                assignment.getStatus() != ReviewAssignmentStatus.COMPLETED)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE);
        }
    }

    private Map<Long, BigDecimal> calculateAverageScores(
            List<ReviewRoundEntry> entries,
            List<ReviewAssignment> activeAssignments,
            Map<Long, Review> reviewsByAssignmentId
    ) {
        Map<Long, List<BigDecimal>> scoresByEntryId = new HashMap<>();
        for (ReviewAssignment assignment : activeAssignments) {
            Review review = reviewsByAssignmentId.get(assignment.getId());
            if (review == null || review.getTotalScore() == null) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE);
            }
            scoresByEntryId
                    .computeIfAbsent(
                            assignment.getReviewRoundEntry().getId(),
                            key -> new ArrayList<>())
                    .add(review.getTotalScore());
        }

        Map<Long, BigDecimal> averages = new HashMap<>();
        for (ReviewRoundEntry entry : entries) {
            List<BigDecimal> scores =
                    scoresByEntryId.getOrDefault(entry.getId(), List.of());
            if (scores.isEmpty()) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE);
            }
            BigDecimal sum = scores.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            averages.put(
                    entry.getId(),
                    sum.divide(
                            BigDecimal.valueOf(scores.size()),
                            2,
                            RoundingMode.HALF_UP)
            );
        }
        return averages;
    }

    private List<RankedEntry> rankEntries(
            List<ReviewRoundEntry> entries,
            Map<Long, BigDecimal> averageByEntryId
    ) {
        List<ReviewRoundEntry> sorted = entries.stream()
                .sorted(Comparator
                        .comparing(
                                (ReviewRoundEntry entry) ->
                                        averageByEntryId.get(entry.getId()),
                                Comparator.reverseOrder())
                        .thenComparing(ReviewRoundEntry::getId))
                .toList();
        List<RankedEntry> ranked = new ArrayList<>();
        BigDecimal previousScore = null;
        int currentRank = 0;
        for (int index = 0; index < sorted.size(); index++) {
            ReviewRoundEntry entry = sorted.get(index);
            BigDecimal score = averageByEntryId.get(entry.getId());
            if (previousScore == null
                    || previousScore.compareTo(score) != 0) {
                currentRank = index + 1;
                previousScore = score;
            }
            ranked.add(new RankedEntry(
                    entry,
                    score,
                    currentRank
            ));
        }
        return ranked;
    }

    private Map<Long, ReviewManualDecisionReq> resolveManualDecisions(
            ReviewRound round,
            List<ReviewRoundEntry> entries,
            ReviewRoundFinalizeReq req
    ) {
        List<ReviewManualDecisionReq> decisions =
                req == null || req.manualDecisions() == null
                        ? List.of()
                        : req.manualDecisions();
        if (round.getDecisionRule() != ReviewRoundDecisionRule.MANUAL) {
            if (!decisions.isEmpty()) {
                throw manualDecisionInvalid();
            }
            return Map.of();
        }
        if (decisions.size() != entries.size()) {
            throw manualDecisionInvalid();
        }

        Map<Long, ReviewManualDecisionReq> byEntryId = new HashMap<>();
        Set<Long> expectedIds = entries.stream()
                .map(ReviewRoundEntry::getId)
                .collect(Collectors.toSet());
        for (ReviewManualDecisionReq decision : decisions) {
            if (decision == null
                    || decision.entryId() == null
                    || !expectedIds.contains(decision.entryId())
                    || (decision.status() != ReviewRoundEntryStatus.SELECTED
                    && decision.status()
                    != ReviewRoundEntryStatus.NOT_SELECTED)
                    || decision.reason() == null
                    || decision.reason().isBlank()
                    || decision.reason().trim().length()
                    > MAX_DECISION_REASON_LENGTH
                    || byEntryId.putIfAbsent(
                    decision.entryId(),
                    decision) != null) {
                throw manualDecisionInvalid();
            }
        }
        if (!new HashSet<>(byEntryId.keySet()).equals(expectedIds)) {
            throw manualDecisionInvalid();
        }
        if (round.isManualWithoutReview()) {
            Set<Integer> rankNumbers = decisions.stream()
                    .map(ReviewManualDecisionReq::rankNo)
                    .collect(Collectors.toSet());
            if (rankNumbers.contains(null)
                    || rankNumbers.size() != entries.size()
                    || java.util.stream.IntStream
                    .rangeClosed(1, entries.size())
                    .anyMatch(rank -> !rankNumbers.contains(rank))) {
                throw manualDecisionInvalid();
            }
        } else if (decisions.stream().anyMatch(decision ->
                decision.rankNo() != null)) {
            throw manualDecisionInvalid();
        }
        return byEntryId;
    }

    private boolean finalizeByRule(
            ReviewRound round,
            ReviewRoundEntry entry,
            RankedEntry rankedEntry,
            LocalDateTime now
    ) {
        ReviewRoundEntryStatus status = switch (round.getDecisionRule()) {
            case TOP_N -> rankedEntry.rankNo() <= round.getSelectCount()
                    ? ReviewRoundEntryStatus.SELECTED
                    : ReviewRoundEntryStatus.NOT_SELECTED;
            case MIN_SCORE -> rankedEntry.finalScore()
                    .compareTo(round.getMinScore()) >= 0
                    ? ReviewRoundEntryStatus.SELECTED
                    : ReviewRoundEntryStatus.NOT_SELECTED;
            case MANUAL -> throw manualDecisionInvalid();
        };
        return entry.finalizeByRule(
                rankedEntry.finalScore(),
                rankedEntry.rankNo(),
                status,
                now
        );
    }

    private boolean finalizeManually(
            ReviewRoundEntry entry,
            RankedEntry rankedEntry,
            ReviewManualDecisionReq decision,
            User admin,
            LocalDateTime now
    ) {
        if (decision == null) {
            throw manualDecisionInvalid();
        }
        return entry.finalizeManually(
                rankedEntry.finalScore(),
                rankedEntry.rankNo(),
                decision.status(),
                admin,
                decision.reason(),
                now
        );
    }

    private User findActiveAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(
                    UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
    }

    private Contest findContest(String publicId, User admin) {
        Contest contest = contestRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.CONTEST_NOT_FOUND));
        if (!contest.getOrganization().getId()
                .equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
        return contest;
    }

    private void validateRoundOrganization(Long roundId, User admin) {
        Long organizationId = reviewRoundRepository
                .findOrganizationIdById(roundId)
                .orElseThrow(this::roundNotFound);
        if (!organizationId.equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private CustomException roundNotFound() {
        return new CustomException(
                ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);
    }

    private CustomException manualDecisionInvalid() {
        return new CustomException(
                ReviewErrorResponseCode.REVIEW_MANUAL_DECISION_INVALID);
    }

    private record RankedEntry(
            ReviewRoundEntry entry,
            BigDecimal finalScore,
            int rankNo
    ) {
    }
}
