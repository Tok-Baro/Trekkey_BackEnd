package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAssignmentRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

    private static final String TARGET_TYPE_REVIEW_STAGE = "REVIEW_STAGE";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final ContestJudgeRepository contestJudgeRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final Clock clock;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public List<ReviewAssignmentRes> prepareAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId,
            Long judgeId,
            ReviewAssignmentPrepareReq req
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateStageOrganization(reviewStageId, admin);

        ContestJudge judge = contestJudgeRepository
                .findByIdAndContestIdForUpdate(judgeId, contest.getId())
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND));
        ContestStage reviewStage = contestStageRepository
                .findByIdForShare(reviewStageId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));
        validateReviewStage(reviewStage, contest);

        List<ReviewRoundEntry> entries = reviewRoundEntryRepository
                .findAllForShareByReviewStageIdOrderByIdAsc(reviewStageId);
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
            return getAssignmentResponses(judgeId, reviewStageId);
        }

        validateReviewStageAssignable(reviewStage);
        entityManager.refresh(contest, LockModeType.PESSIMISTIC_READ);
        validateContestReviewing(contest);
        validateEntriesAssignable(entries);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dueAt = resolveDueAt(req, reviewStage, now);
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
                TARGET_TYPE_REVIEW_STAGE,
                reviewStageId,
                "judgeId=" + judgeId
                        + ", assignmentCount=" + changedAssignments.size()
        );

        return getAssignmentResponses(judgeId, reviewStageId);
    }

    @Override
    public List<ReviewAssignmentRes> getAssignments(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId,
            Long judgeId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        ContestStage reviewStage = findReviewStage(
                reviewStageId,
                contest,
                admin
        );
        findJudge(judgeId, contest.getId());

        return getAssignmentResponses(judgeId, reviewStage.getId());
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

    private ContestStage findReviewStage(
            Long reviewStageId,
            Contest contest,
            User admin
    ) {
        validateStageOrganization(reviewStageId, admin);
        ContestStage reviewStage = contestStageRepository
                .findById(reviewStageId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));
        validateReviewStage(reviewStage, contest);
        return reviewStage;
    }

    private ContestJudge findJudge(Long judgeId, Long contestId) {
        return contestJudgeRepository
                .findByIdAndContestId(judgeId, contestId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND));
    }

    private void validateStageOrganization(Long stageId, User admin) {
        Long organizationId = contestStageRepository
                .findOrganizationIdById(stageId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));
        if (!organizationId.equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private void validateReviewStage(
            ContestStage reviewStage,
            Contest contest
    ) {
        if (!reviewStage.getContest().getId().equals(contest.getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.STAGE_NOT_FOUND);
        }
        if (!reviewStage.getStageType().supportsReviewCriteria()) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_STAGE_INVALID);
        }
    }

    private void validateReviewStageAssignable(
            ContestStage reviewStage
    ) {
        if (reviewStage.getStatus() != StageStatus.PREPARING
                && reviewStage.getStatus() != StageStatus.OPEN) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED);
        }
    }

    private void validateContestReviewing(Contest contest) {
        if (contest.getStatus() != ContestStatus.REVIEWING) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_CONTEST_NOT_REVIEWING);
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
            ContestStage reviewStage,
            LocalDateTime now
    ) {
        LocalDateTime dueAt =
                req == null || req.dueAt() == null
                        ? reviewStage.getEndsAt()
                        : req.dueAt();
        if (dueAt != null
                && (!dueAt.isAfter(now)
                || (reviewStage.getEndsAt() != null
                && dueAt.isAfter(reviewStage.getEndsAt())))) {
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
            Long reviewStageId
    ) {
        return reviewAssignmentRepository
                .findAllWithDetailsByJudgeIdAndReviewStageId(
                        judgeId,
                        reviewStageId
                )
                .stream()
                .map(ReviewAssignmentRes::from)
                .toList();
    }
}
