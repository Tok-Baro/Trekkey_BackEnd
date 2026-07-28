package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundEntryPrepareReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundEntryRes;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final ReviewRepository reviewRepository;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    @Transactional
    public List<ReviewRoundEntryRes> prepareEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId
    ) {
        return prepareEntries(
                adminUserId,
                contestPublicId,
                reviewRoundId,
                null);
    }

    @Override
    @Transactional
    public List<ReviewRoundEntryRes> prepareEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId,
            ReviewRoundEntryPrepareReq req
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateRoundOrganization(reviewRoundId, admin);
        ReviewRound reviewRound = reviewRoundRepository
                .findByIdForUpdate(reviewRoundId)
                .orElseThrow(this::reviewRoundNotFound);
        validateRoundContest(reviewRound, contest);

        List<ReviewCriterion> criteria = reviewCriterionRepository
                .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(reviewRoundId));
        List<ReviewRoundEntry> existingEntries =
                reviewRoundEntryRepository
                        .findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                                reviewRoundId);
        if (reviewRound.getStatus() != ReviewRoundStatus.PREPARING) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_PREPARATION_NOT_ALLOWED);
        }
        validateReviewRoundConfiguration(reviewRound, criteria);
        if (!existingEntries.isEmpty()
                && reviewRound.getTargetType()
                != ReviewRoundTargetType.ALL_SUBMISSIONS) {
            return toResponses(existingEntries);
        }

        List<String> selectedSubmissionPublicIds =
                resolveSelectedSubmissionPublicIds(reviewRound, req);
        teamRepository.findAllForUpdateByContestIdOrderByIdAsc(contest.getId());
        List<Submission> eligibleSubmissions = submissionRepository
                .findAllForUpdateByContestIdAndStatusAndTeamStatus(
                        contest.getId(),
                        SubmissionStatus.SUBMITTED,
                        TeamStatus.APPROVED
                );
        List<Submission> submissions = selectTargetSubmissions(
                reviewRound,
                eligibleSubmissions,
                selectedSubmissionPublicIds);
        if (submissions.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ENTRY_SUBMISSION_REQUIRED);
        }

        Set<Long> existingSubmissionIds = existingEntries.stream()
                .map(entry -> entry.getSubmission().getId())
                .collect(Collectors.toSet());
        List<ReviewRoundEntry> newEntries = submissions.stream()
                .filter(submission ->
                        !existingSubmissionIds.contains(submission.getId()))
                .map(submission -> ReviewRoundEntry.builder()
                        .reviewRound(reviewRound)
                        .submission(submission)
                        .status(ReviewRoundEntryStatus.ELIGIBLE)
                        .build())
                .toList();
        if (newEntries.isEmpty()) {
            return toResponses(existingEntries);
        }

        List<ReviewRoundEntry> savedEntries;
        try {
            savedEntries =
                    reviewRoundEntryRepository.saveAllAndFlush(newEntries);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ENTRY_DUPLICATED);
        }

        List<ReviewRoundEntry> entries = new ArrayList<>(existingEntries);
        entries.addAll(savedEntries);
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
    @Transactional
    public void resetEntries(
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
        if (reviewRound.getStatus() != ReviewRoundStatus.PREPARING) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_RESET_NOT_ALLOWED);
        }

        List<ReviewRoundEntry> entries = reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(reviewRoundId);
        if (entries.isEmpty()) {
            return;
        }
        List<Long> entryIds = entries.stream()
                .map(ReviewRoundEntry::getId)
                .toList();
        List<ReviewAssignment> assignments = reviewAssignmentRepository
                .findAllForUpdateByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                        entryIds);
        List<Long> assignmentIds = assignments.stream()
                .map(ReviewAssignment::getId)
                .toList();
        if (assignments.stream().anyMatch(assignment ->
                assignment.getStatus() == ReviewAssignmentStatus.COMPLETED)
                || (!assignmentIds.isEmpty()
                && reviewRepository.existsByAssignmentIdIn(assignmentIds))) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_RESET_NOT_ALLOWED);
        }

        if (!assignments.isEmpty()) {
            reviewAssignmentRepository.deleteAllInBatch(assignments);
            reviewAssignmentRepository.flush();
        }
        reviewRoundEntryRepository.deleteAllInBatch(entries);
        reviewRoundEntryRepository.flush();

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.REVIEW_ENTRIES_RESET,
                TARGET_TYPE_REVIEW_ROUND,
                reviewRound.getId(),
                "contestId=" + contest.getId()
                        + ", entryCount=" + entries.size()
                        + ", assignmentCount=" + assignments.size()
        );
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
        if (activeCriteria.isEmpty()
                && !reviewRound.isManualWithoutReview()) {
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

    private List<String> resolveSelectedSubmissionPublicIds(
            ReviewRound reviewRound,
            ReviewRoundEntryPrepareReq req
    ) {
        return switch (reviewRound.getTargetType()) {
            case ALL_SUBMISSIONS -> List.of();
            case PREVIOUS_SELECTED ->
                    previousSelectedSubmissionPublicIds(reviewRound);
            case MANUAL -> manualSubmissionPublicIds(req);
        };
    }

    private List<String> previousSelectedSubmissionPublicIds(
            ReviewRound reviewRound
    ) {
        ReviewRound previousRound = reviewRoundRepository
                .findFirstByContestIdAndRoundNoLessThanOrderByRoundNoDesc(
                        reviewRound.getContest().getId(),
                        reviewRound.getRoundNo())
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ENTRY_PREVIOUS_ROUND_REQUIRED));
        if (previousRound.getStatus() != ReviewRoundStatus.FINALIZED) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_PREVIOUS_ROUND_REQUIRED);
        }

        List<String> selectedSubmissionPublicIds =
                reviewRoundEntryRepository
                        .findAllForShareByReviewRoundIdAndStatusOrderByRankNoAscIdAsc(
                                previousRound.getId(),
                                ReviewRoundEntryStatus.SELECTED)
                        .stream()
                        .map(entry -> entry.getSubmission().getPublicId())
                        .distinct()
                        .toList();
        if (selectedSubmissionPublicIds.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_PREVIOUS_SELECTION_REQUIRED);
        }
        return selectedSubmissionPublicIds;
    }

    private List<String> manualSubmissionPublicIds(
            ReviewRoundEntryPrepareReq req
    ) {
        if (req == null || req.submissionPublicIds() == null) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_MANUAL_SUBMISSIONS_REQUIRED);
        }

        LinkedHashSet<String> publicIds = new LinkedHashSet<>();
        for (String publicId : req.submissionPublicIds()) {
            if (publicId == null || publicId.isBlank()) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ENTRY_SUBMISSION_INVALID);
            }
            publicIds.add(publicId.trim());
        }
        if (publicIds.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_MANUAL_SUBMISSIONS_REQUIRED);
        }
        return List.copyOf(publicIds);
    }

    private List<Submission> selectTargetSubmissions(
            ReviewRound reviewRound,
            List<Submission> eligibleSubmissions,
            List<String> selectedSubmissionPublicIds
    ) {
        if (reviewRound.getTargetType()
                == ReviewRoundTargetType.ALL_SUBMISSIONS) {
            return eligibleSubmissions;
        }

        Map<String, Submission> eligibleByPublicId =
                eligibleSubmissions.stream().collect(Collectors.toMap(
                        Submission::getPublicId,
                        Function.identity()));
        List<Submission> selectedSubmissions = new ArrayList<>();
        for (String publicId : selectedSubmissionPublicIds) {
            Submission submission = eligibleByPublicId.get(publicId);
            if (submission == null) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ENTRY_SUBMISSION_INVALID);
            }
            selectedSubmissions.add(submission);
        }
        return selectedSubmissions;
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
