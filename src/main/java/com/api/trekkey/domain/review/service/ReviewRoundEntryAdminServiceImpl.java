package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
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

    private static final String TARGET_TYPE_REVIEW_ROUND = "REVIEW_ROUND";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewCriterionRepository reviewCriterionRepository;
    private final TeamRepository teamRepository;
    private final SubmissionRepository submissionRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final Clock clock;

    @Override
    @Transactional
    public List<ReviewRoundEntryRes> prepareEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateRoundOrganization(reviewRoundId, admin);
        ReviewRound reviewRound = reviewRoundRepository
                .findByIdForUpdate(reviewRoundId)
                .orElseThrow(this::reviewRoundNotFound);
        validateRoundContest(reviewRound, contest);
        validateSupportedTargetType(reviewRound);

        List<ReviewCriterion> criteria = reviewCriterionRepository
                .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(reviewRoundId));
        List<ReviewRoundEntry> existingEntries =
                reviewRoundEntryRepository
                        .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                                reviewRoundId);
        if (!existingEntries.isEmpty()) {
            return toResponses(existingEntries);
        }
        if (reviewRound.getStatus() != ReviewRoundStatus.PREPARING) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_PREPARATION_NOT_ALLOWED);
        }
        validateReviewRoundConfiguration(reviewRound, criteria);

        teamRepository.findAllForUpdateByContestIdOrderByIdAsc(contest.getId());
        List<Submission> submissions = submissionRepository
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
                        ReviewErrorResponseCode
                                .REVIEW_ENTRY_SUBMISSION_INVALID);
            }
        }

        List<ReviewRoundEntry> entries = submissions.stream()
                .map(submission -> ReviewRoundEntry.builder()
                        .reviewRound(reviewRound)
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
                TARGET_TYPE_REVIEW_ROUND,
                reviewRound.getId(),
                "contestId=" + contest.getId()
                        + ", entryCount=" + entries.size()
        );

        return toResponses(entries);
    }

    @Override
    public List<ReviewRoundEntryRes> getEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        ReviewRound reviewRound = findReviewRound(
                reviewRoundId,
                contest,
                admin
        );

        return toResponses(reviewRoundEntryRepository
                .findAllByReviewRoundIdOrderByCreatedAtAscIdAsc(
                        reviewRound.getId()));
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

    private void validateSupportedTargetType(ReviewRound reviewRound) {
        if (reviewRound.getTargetType()
                != ReviewRoundTargetType.ALL_SUBMISSIONS) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED);
        }
    }

    private void validateReviewRoundConfiguration(
            ReviewRound reviewRound,
            List<ReviewCriterion> criteria
    ) {
        if (!reviewRound.hasValidConfigurationForOpening()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CONFIGURATION_INVALID);
        }
        List<ReviewCriterion> activeCriteria = criteria.stream()
                .filter(ReviewCriterion::isActive)
                .toList();
        if (activeCriteria.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CRITERION_REQUIRED);
        }
        if (activeCriteria.stream()
                .anyMatch(criterion -> criterion.getMaxScore() < 1)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CONFIGURATION_INVALID);
        }
    }

    private CustomException reviewRoundNotFound() {
        return new CustomException(
                ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);
    }

    private List<ReviewRoundEntryRes> toResponses(
            List<ReviewRoundEntry> entries
    ) {
        return entries.stream()
                .map(ReviewRoundEntryRes::from)
                .toList();
    }
}
