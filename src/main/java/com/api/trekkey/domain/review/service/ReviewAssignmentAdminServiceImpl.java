package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAssignmentRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewAssignmentAdminServiceImpl
        implements ReviewAssignmentAdminService {

    private static final String TARGET_TYPE_REVIEW_ROUND = "REVIEW_ROUND";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ContestJudgeRepository contestJudgeRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final Clock clock;

    @Override
    @Transactional
    public List<ReviewAssignmentRes> prepareAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            Long judgeId,
            ReviewAssignmentPrepareReq req
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateRoundOrganization(reviewRoundId, admin);

        ContestJudge judge = contestJudgeRepository
                .findByIdAndContestIdForUpdate(judgeId, contest.getId())
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND));
        ReviewRound reviewRound = reviewRoundRepository
                .findByIdForShare(reviewRoundId)
                .orElseThrow(this::reviewRoundNotFound);
        validateRoundContest(reviewRound, contest);

        List<ReviewRoundEntry> entries = reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByIdAsc(reviewRoundId);
        if (entries.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ASSIGNMENT_ENTRY_REQUIRED);
        }

        List<Long> entryIds = entries.stream()
                .map(ReviewRoundEntry::getId)
                .toList();
        List<ReviewAssignment> existingAssignments =
                reviewAssignmentRepository
                        .findAllForUpdateByJudgeIdAndEntryIdIn(
                                judgeId,
                                entryIds
                        );
        if (hasAllActiveAssignments(entries, existingAssignments)) {
            return getAssignmentResponses(judgeId, reviewRoundId);
        }

        validateRoundAssignable(reviewRound);
        validateEntriesAssignable(entries);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dueAt = resolveDueAt(req, reviewRound, now);
        Map<Long, ReviewAssignment> existingByEntryId = new HashMap<>();
        existingAssignments.forEach(assignment ->
                existingByEntryId.put(
                        assignment.getReviewRoundEntry().getId(),
                        assignment
                ));

        List<ReviewAssignment> changedAssignments = new ArrayList<>();
        for (ReviewRoundEntry entry : entries) {
            ReviewAssignment existing = existingByEntryId.get(entry.getId());
            if (existing == null) {
                changedAssignments.add(ReviewAssignment.builder()
                        .contestJudge(judge)
                        .reviewRoundEntry(entry)
                        .status(ReviewAssignmentStatus.ASSIGNED)
                        .assignedAt(now)
                        .dueAt(dueAt)
                        .build());
            } else if (existing.getStatus()
                    == ReviewAssignmentStatus.CANCELED) {
                if (!existing.reassign(now, dueAt)) {
                    throw new CustomException(
                            ReviewErrorResponseCode
                                    .REVIEW_ASSIGNMENT_ENTRY_INVALID);
                }
                changedAssignments.add(existing);
            }
        }

        try {
            reviewAssignmentRepository
                    .saveAllAndFlush(changedAssignments);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ASSIGNMENT_DUPLICATED);
        }

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.REVIEW_ASSIGNMENTS_PREPARE,
                TARGET_TYPE_REVIEW_ROUND,
                reviewRoundId,
                "judgeId=" + judgeId
                        + ", assignmentCount=" + changedAssignments.size()
        );

        return getAssignmentResponses(judgeId, reviewRoundId);
    }

    @Override
    public List<ReviewAssignmentRes> getAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            Long judgeId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        ReviewRound reviewRound = findReviewRound(
                reviewRoundId,
                contest,
                admin
        );
        findJudge(judgeId, contest.getId());

        return getAssignmentResponses(judgeId, reviewRound.getId());
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
        Contest contest = contestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.CONTEST_NOT_FOUND));
        if (!contest.getOrganization().getId()
                .equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
        return contest;
    }

    private ReviewRound findReviewRound(
            Long reviewRoundId,
            Contest contest,
            User admin
    ) {
        validateRoundOrganization(reviewRoundId, admin);
        ReviewRound reviewRound = reviewRoundRepository
                .findById(reviewRoundId)
                .orElseThrow(this::reviewRoundNotFound);
        validateRoundContest(reviewRound, contest);
        return reviewRound;
    }

    private ContestJudge findJudge(Long judgeId, Long contestId) {
        return contestJudgeRepository
                .findByIdAndContestId(judgeId, contestId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND));
    }

    private void validateRoundOrganization(Long roundId, User admin) {
        Long organizationId = reviewRoundRepository
                .findOrganizationIdById(roundId)
                .orElseThrow(this::reviewRoundNotFound);
        if (!organizationId.equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private void validateRoundContest(
            ReviewRound reviewRound,
            Contest contest
    ) {
        if (!reviewRound.getContest().getId().equals(contest.getId())) {
            throw reviewRoundNotFound();
        }
    }

    private void validateRoundAssignable(ReviewRound reviewRound) {
        if (reviewRound.getStatus() != ReviewRoundStatus.PREPARING
                && reviewRound.getStatus() != ReviewRoundStatus.OPEN) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED);
        }
    }

    private void validateEntriesAssignable(
            List<ReviewRoundEntry> entries
    ) {
        boolean invalidEntryExists = entries.stream()
                .anyMatch(entry ->
                        entry.getStatus() != ReviewRoundEntryStatus.ELIGIBLE
                                && entry.getStatus()
                                != ReviewRoundEntryStatus.IN_REVIEW);
        if (invalidEntryExists) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ASSIGNMENT_ENTRY_INVALID);
        }
    }

    private LocalDateTime resolveDueAt(
            ReviewAssignmentPrepareReq req,
            ReviewRound reviewRound,
            LocalDateTime now
    ) {
        LocalDateTime dueAt =
                req == null || req.dueAt() == null
                        ? reviewRound.getEndsAt()
                        : req.dueAt();
        if (dueAt != null
                && (!dueAt.isAfter(now)
                || (reviewRound.getEndsAt() != null
                && dueAt.isAfter(reviewRound.getEndsAt())))) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ASSIGNMENT_DUE_AT_INVALID);
        }
        return dueAt;
    }

    private boolean hasAllActiveAssignments(
            List<ReviewRoundEntry> entries,
            List<ReviewAssignment> assignments
    ) {
        if (entries.size() != assignments.size()) {
            return false;
        }
        return assignments.stream()
                .noneMatch(assignment ->
                        assignment.getStatus()
                                == ReviewAssignmentStatus.CANCELED);
    }

    private List<ReviewAssignmentRes> getAssignmentResponses(
            Long judgeId,
            Long reviewRoundId
    ) {
        return reviewAssignmentRepository
                .findAllWithDetailsByJudgeIdAndReviewRoundId(
                        judgeId,
                        reviewRoundId
                )
                .stream()
                .map(ReviewAssignmentRes::from)
                .toList();
    }

    private CustomException reviewRoundNotFound() {
        return new CustomException(
                ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);
    }
}
