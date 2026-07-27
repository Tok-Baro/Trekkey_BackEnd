package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageTargetType;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundEntryRes;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamRepository;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewRoundEntryAdminServiceImpl
        implements ReviewRoundEntryAdminService {

    private static final String TARGET_TYPE_REVIEW_STAGE = "REVIEW_STAGE";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final ReviewCriterionRepository reviewCriterionRepository;
    private final TeamRepository teamRepository;
    private final SubmissionRepository submissionRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final Clock clock;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public List<ReviewRoundEntryRes> prepareEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateStageOrganization(reviewStageId, admin);
        List<ContestStage> lockedStages =
                contestStageRepository
                        .findAllForUpdateByContestIdOrderBySequenceNoAsc(
                                contest.getId());
        ContestStage reviewStage =
                findReviewStage(reviewStageId, lockedStages);
        validateReviewStage(reviewStage);
        List<ReviewCriterion> criteria =
                reviewCriterionRepository
                        .findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                                List.of(reviewStageId));

        List<ReviewRoundEntry> existingEntries =
                reviewRoundEntryRepository
                        .findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                                reviewStageId);
        if (!existingEntries.isEmpty()) {
            return toResponses(existingEntries);
        }
        if (reviewStage.getStatus() != StageStatus.PREPARING) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ENTRY_PREPARATION_NOT_ALLOWED);
        }
        validateReviewStageConfiguration(reviewStage, criteria);
        entityManager.refresh(contest, LockModeType.PESSIMISTIC_READ);
        validateContestReviewing(contest);

        validateSubmissionStageCompleted(lockedStages);
        teamRepository.findAllForUpdateByContestIdOrderByIdAsc(contest.getId());
        List<Submission> submissions =
                submissionRepository
                        .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                                contest.getId(),
                                SubmissionStatus.SUBMITTED,
                                TeamStatus.APPROVED
                        );
        if (submissions.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ENTRY_SUBMISSION_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (Submission submission : submissions) {
            if (!submission.isFinalized() && !submission.finalizeAt(now)) {
                throw new CustomException(
                        ReviewErrorResponseCode.REVIEW_ENTRY_SUBMISSION_INVALID);
            }
        }

        List<ReviewRoundEntry> entries = submissions.stream()
                .map(submission -> ReviewRoundEntry.builder()
                        .reviewStage(reviewStage)
                        .submission(submission)
                        .status(ReviewRoundEntryStatus.ELIGIBLE)
                        .build())
                .toList();

        try {
            entries = reviewRoundEntryRepository.saveAllAndFlush(entries);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ENTRY_DUPLICATED);
        }

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.REVIEW_ENTRIES_PREPARE,
                TARGET_TYPE_REVIEW_STAGE,
                reviewStage.getId(),
                "contestId=" + contest.getId() + ", entryCount=" + entries.size()
        );

        return toResponses(entries);
    }

    @Override
    public List<ReviewRoundEntryRes> getEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        ContestStage reviewStage =
                findReviewStage(reviewStageId, contest, admin);
        validateReviewStageType(reviewStage);

        return toResponses(reviewRoundEntryRepository
                .findAllByReviewStageIdOrderByCreatedAtAscIdAsc(reviewStageId));
    }

    private User findActiveAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
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
            List<ContestStage> lockedStages
    ) {
        return lockedStages.stream()
                .filter(stage -> stage.getId().equals(reviewStageId))
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));
    }

    private ContestStage findReviewStage(
            Long reviewStageId,
            Contest contest,
            User admin
    ) {
        validateStageOrganization(reviewStageId, admin);
        ContestStage stage = contestStageRepository.findById(reviewStageId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));
        validateStageContest(stage, contest);
        return stage;
    }

    private void validateStageOrganization(Long stageId, User admin) {
        Long organizationId = contestStageRepository.findOrganizationIdById(stageId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));
        if (!organizationId.equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private void validateStageContest(ContestStage stage, Contest contest) {
        if (!stage.getContest().getId().equals(contest.getId())) {
            throw new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND);
        }
    }

    private void validateReviewStage(ContestStage stage) {
        validateReviewStageType(stage);
        if (stage.getTargetType() != StageTargetType.ALL_SUBMISSIONS) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED);
        }
    }

    private void validateReviewStageType(ContestStage stage) {
        if (!stage.getStageType().supportsReviewCriteria()) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_STAGE_INVALID);
        }
    }

    private void validateContestReviewing(Contest contest) {
        if (contest.getStatus() != ContestStatus.REVIEWING) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_CONTEST_NOT_REVIEWING);
        }
    }

    private void validateReviewStageConfiguration(
            ContestStage reviewStage,
            List<ReviewCriterion> criteria
    ) {
        if (!reviewStage.hasValidConfigurationForOpening()) {
            throw new CustomException(
                    ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);
        }
        List<ReviewCriterion> activeCriteria = criteria.stream()
                .filter(ReviewCriterion::isActive)
                .toList();
        if (activeCriteria.isEmpty()) {
            throw new CustomException(
                    ContestErrorResponseCode.REVIEW_CRITERION_REQUIRED);
        }
        if (activeCriteria.stream()
                .anyMatch(criterion -> criterion.getMaxScore() < 1)) {
            throw new CustomException(
                    ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);
        }
    }

    private void validateSubmissionStageCompleted(
            List<ContestStage> lockedStages
    ) {
        List<ContestStage> submissionStages = lockedStages.stream()
                .filter(stage -> stage.getStageType() == StageType.SUBMISSION)
                .toList();
        if (submissionStages.size() != 1
                || submissionStages.getFirst().getStatus()
                != StageStatus.COMPLETED) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_SUBMISSION_STAGE_NOT_COMPLETED);
        }
    }

    private List<ReviewRoundEntryRes> toResponses(
            List<ReviewRoundEntry> entries
    ) {
        return entries.stream()
                .map(ReviewRoundEntryRes::from)
                .toList();
    }
}
