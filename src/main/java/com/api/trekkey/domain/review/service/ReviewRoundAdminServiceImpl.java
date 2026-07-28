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
import com.api.trekkey.domain.review.web.dto.request.ReviewRoundCriterionReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewRoundSaveReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewRoundAdminServiceImpl implements ReviewRoundAdminService {

    private static final String TARGET_TYPE_REVIEW_ROUND = "REVIEW_ROUND";
    private static final String CRITERION_CODE_REGEX =
            "[A-Za-z0-9][A-Za-z0-9_-]*";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewCriterionRepository reviewCriterionRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final AdminAuditLogger adminAuditLogger;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public ReviewRoundRes createRound(
            Long adminUserId,
            String contestPublicId,
            ReviewRoundSaveReq req
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateRoundRequest(req);

        // 같은 대회의 라운드 번호를 동시에 만들지 못하도록 대회를 먼저 잠근다.
        entityManager.refresh(contest, LockModeType.PESSIMISTIC_WRITE);
        List<ReviewRound> existingRounds =
                reviewRoundRepository
                        .findAllForUpdateByContestIdOrderByRoundNoAsc(
                                contest.getId());
        validateRoundNoAvailable(existingRounds, null, req.roundNo());

        List<CriterionPlan> criterionPlans =
                resolveCriterionPlans(req.criteria(), List.of());
        ReviewRound round = ReviewRound.builder()
                .contest(contest)
                .roundNo(req.roundNo())
                .name(req.name().trim())
                .status(ReviewRoundStatus.PREPARING)
                .startsAt(req.startsAt())
                .endsAt(req.endsAt())
                .targetType(req.targetType())
                .decisionRule(req.decisionRule())
                .selectCount(req.selectCount())
                .minScore(req.minScore())
                .build();

        try {
            round = reviewRoundRepository.saveAndFlush(round);
            List<ReviewCriterion> criteria =
                    synchronizeCriteria(round, List.of(), criterionPlans);
            reviewCriterionRepository.flush();

            adminAuditLogger.log(
                    admin.getId(),
                    admin.getOrganization().getId(),
                    AuditAction.REVIEW_ROUND_CREATE,
                    TARGET_TYPE_REVIEW_ROUND,
                    round.getId(),
                    "contestId=" + contest.getId()
                            + ", roundNo=" + round.getRoundNo()
            );
            return ReviewRoundRes.from(round, criteria);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_DUPLICATED);
        }
    }

    @Override
    public List<ReviewRoundRes> getRounds(
            Long adminUserId,
            String contestPublicId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        List<ReviewRound> rounds =
                reviewRoundRepository
                        .findAllByContestIdOrderByRoundNoAsc(contest.getId());
        if (rounds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ReviewCriterion>> criteriaByRoundId =
                groupCriteriaByRoundId(
                        reviewCriterionRepository
                                .findAllByReviewRoundIdInOrderBySortOrderAsc(
                                        rounds.stream()
                                                .map(ReviewRound::getId)
                                                .toList()));

        return rounds.stream()
                .map(round -> ReviewRoundRes.from(
                        round,
                        criteriaByRoundId.getOrDefault(
                                round.getId(),
                                List.of())))
                .toList();
    }

    @Override
    public ReviewRoundRes getRound(
            Long adminUserId,
            String contestPublicId,
            Long roundId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateRoundOrganization(roundId, admin);
        ReviewRound round = reviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND));
        validateRoundContest(round, contest);

        List<ReviewCriterion> criteria =
                reviewCriterionRepository
                        .findAllByReviewRoundIdInOrderBySortOrderAsc(
                                List.of(roundId));
        return ReviewRoundRes.from(round, criteria);
    }

    @Override
    @Transactional
    public ReviewRoundRes updateRound(
            Long adminUserId,
            String contestPublicId,
            Long roundId,
            ReviewRoundSaveReq req
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateRoundRequest(req);
        validateRoundOrganization(roundId, admin);

        List<ReviewRound> lockedRounds =
                reviewRoundRepository
                        .findAllForUpdateByContestIdOrderByRoundNoAsc(
                                contest.getId());
        ReviewRound round = findRound(roundId, lockedRounds);
        if (!round.isConfigurationEditable()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CONFIGURATION_LOCKED);
        }
        validateRoundNoAvailable(lockedRounds, roundId, req.roundNo());

        List<ReviewCriterion> existingCriteria =
                reviewCriterionRepository
                        .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                                List.of(roundId));
        List<ReviewRoundEntry> existingEntries =
                reviewRoundEntryRepository
                        .findAllForShareByReviewRoundIdOrderByIdAsc(roundId);
        if (!existingEntries.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CONFIGURATION_LOCKED);
        }
        List<CriterionPlan> criterionPlans =
                resolveCriterionPlans(req.criteria(), existingCriteria);

        round.updateConfiguration(
                req.name().trim(),
                req.roundNo(),
                req.startsAt(),
                req.endsAt(),
                req.targetType(),
                req.decisionRule(),
                req.selectCount(),
                req.minScore()
        );

        try {
            List<ReviewCriterion> criteria =
                    synchronizeCriteria(
                            round,
                            existingCriteria,
                            criterionPlans);
            reviewRoundRepository.flush();
            reviewCriterionRepository.flush();

            adminAuditLogger.log(
                    admin.getId(),
                    admin.getOrganization().getId(),
                    AuditAction.REVIEW_ROUND_UPDATE,
                    TARGET_TYPE_REVIEW_ROUND,
                    round.getId(),
                    "contestId=" + contest.getId()
                            + ", roundNo=" + round.getRoundNo()
            );
            return ReviewRoundRes.from(round, criteria);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_DUPLICATED);
        }
    }

    @Override
    @Transactional
    public ReviewRoundRes openRound(
            Long adminUserId,
            String contestPublicId,
            Long roundId
    ) {
        User admin = findActiveAdmin(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        validateRoundOrganization(roundId, admin);
        ReviewRound round = reviewRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND));
        validateRoundContest(round, contest);

        if (round.getStatus() != ReviewRoundStatus.PREPARING) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_STATUS_TRANSITION_INVALID);
        }
        if (!round.hasValidConfigurationForOpening()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CONFIGURATION_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(round.getEndsAt())) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_OPEN_WINDOW_EXPIRED);
        }

        List<ReviewCriterion> criteria =
                reviewCriterionRepository
                        .findAllForShareByReviewRoundIdOrderBySortOrderAsc(
                                roundId);
        List<ReviewCriterion> activeCriteria = activeCriteria(criteria);
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

        List<ReviewRoundEntry> entries =
                reviewRoundEntryRepository
                        .findAllForUpdateByReviewRoundIdOrderByIdAsc(roundId);
        if (entries.isEmpty()) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ENTRY_REQUIRED);
        }
        if (entries.stream().anyMatch(entry ->
                entry.getStatus() != ReviewRoundEntryStatus.ELIGIBLE)) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_ENTRY_INVALID);
        }

        if (!round.open()) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_STATUS_TRANSITION_INVALID);
        }
        entries.forEach(ReviewRoundEntry::startReview);
        reviewRoundRepository.flush();
        reviewRoundEntryRepository.flush();

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.REVIEW_ROUND_OPEN,
                TARGET_TYPE_REVIEW_ROUND,
                round.getId(),
                "contestId=" + contest.getId()
                        + ", entryCount=" + entries.size()
        );

        return ReviewRoundRes.from(round, activeCriteria);
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

    private void validateRoundOrganization(Long roundId, User admin) {
        Long organizationId =
                reviewRoundRepository.findOrganizationIdById(roundId)
                        .orElseThrow(() -> new CustomException(
                                ReviewErrorResponseCode
                                        .REVIEW_ROUND_NOT_FOUND));
        if (!organizationId.equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private void validateRoundContest(
            ReviewRound round,
            Contest contest
    ) {
        if (!round.getContest().getId().equals(contest.getId())) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);
        }
    }

    private ReviewRound findRound(
            Long roundId,
            List<ReviewRound> rounds
    ) {
        return rounds.stream()
                .filter(round -> round.getId().equals(roundId))
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND));
    }

    private void validateRoundNoAvailable(
            List<ReviewRound> rounds,
            Long currentRoundId,
            int roundNo
    ) {
        boolean duplicated = rounds.stream().anyMatch(round ->
                round.getRoundNo() == roundNo
                        && !Objects.equals(round.getId(), currentRoundId));
        if (duplicated) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_DUPLICATED);
        }
    }

    private void validateRoundRequest(ReviewRoundSaveReq req) {
        if (req != null
                && req.targetType() != null
                && req.targetType()
                        != ReviewRoundTargetType.ALL_SUBMISSIONS) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED);
        }
        if (req == null
                || req.roundNo() == null
                || req.roundNo() < 1
                || req.name() == null
                || req.name().isBlank()
                || req.name().trim().length() > 100
                || req.startsAt() == null
                || req.endsAt() == null
                || !req.startsAt().isBefore(req.endsAt())
                || req.targetType() == null
                || req.decisionRule() == null
                || !hasValidDecisionConfiguration(req)) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CONFIGURATION_INVALID);
        }
    }

    private boolean hasValidDecisionConfiguration(ReviewRoundSaveReq req) {
        return switch (req.decisionRule()) {
            case TOP_N -> req.selectCount() != null
                    && req.selectCount() > 0
                    && req.minScore() == null;
            case MIN_SCORE -> req.selectCount() == null
                    && req.minScore() != null
                    && req.minScore().compareTo(BigDecimal.ZERO) >= 0;
            case MANUAL -> req.selectCount() == null
                    && req.minScore() == null;
        };
    }

    private List<CriterionPlan> resolveCriterionPlans(
            List<ReviewRoundCriterionReq> requestedCriteria,
            List<ReviewCriterion> existingCriteria
    ) {
        List<ReviewRoundCriterionReq> criterionReqs =
                requestedCriteria == null ? List.of() : requestedCriteria;
        Map<Long, ReviewCriterion> existingById = new HashMap<>();
        Map<String, ReviewCriterion> existingByCode = new HashMap<>();
        for (ReviewCriterion criterion : existingCriteria) {
            existingById.put(criterion.getId(), criterion);
            existingByCode.put(codeKey(criterion.getCode()), criterion);
        }

        List<CriterionPlan> plans = new ArrayList<>();
        Set<Long> selectedCriterionIds = new HashSet<>();
        Set<String> selectedCodes = new HashSet<>();
        Set<Integer> selectedSortOrders = new HashSet<>();

        for (ReviewRoundCriterionReq criterionReq : criterionReqs) {
            validateCriterion(criterionReq);
            String requestedCode = criterionReq.code().trim();
            ReviewCriterion criterion;
            String code;

            if (criterionReq.id() != null) {
                criterion = existingById.get(criterionReq.id());
                if (criterion == null) {
                    throw new CustomException(
                            ReviewErrorResponseCode
                                    .REVIEW_ROUND_CRITERION_NOT_FOUND);
                }
                if (!codeKey(criterion.getCode())
                        .equals(codeKey(requestedCode))) {
                    throw new CustomException(
                            ReviewErrorResponseCode
                                    .REVIEW_ROUND_CRITERION_CODE_IMMUTABLE);
                }
                code = criterion.getCode();
            } else {
                criterion = existingByCode.get(codeKey(requestedCode));
                code = criterion == null
                        ? requestedCode
                        : criterion.getCode();
            }

            if (criterion != null
                    && !selectedCriterionIds.add(criterion.getId())) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_CRITERION_DUPLICATED);
            }
            if (!selectedCodes.add(codeKey(code))
                    || !selectedSortOrders.add(criterionReq.sortOrder())) {
                throw new CustomException(
                        ReviewErrorResponseCode
                                .REVIEW_ROUND_CRITERION_DUPLICATED);
            }

            plans.add(new CriterionPlan(
                    criterion,
                    code,
                    criterionReq.label().trim(),
                    criterionReq.maxScore(),
                    criterionReq.sortOrder()
            ));
        }
        return plans;
    }

    private void validateCriterion(ReviewRoundCriterionReq criterionReq) {
        if (criterionReq == null
                || criterionReq.code() == null
                || criterionReq.code().isBlank()
                || criterionReq.code().length() > 60
                || !criterionReq.code().matches(CRITERION_CODE_REGEX)
                || criterionReq.label() == null
                || criterionReq.label().isBlank()
                || criterionReq.label().trim().length() > 60
                || criterionReq.maxScore() == null
                || criterionReq.maxScore() < 1
                || criterionReq.sortOrder() == null
                || criterionReq.sortOrder() < 1) {
            throw new CustomException(
                    ReviewErrorResponseCode
                            .REVIEW_ROUND_CRITERION_INVALID);
        }
    }

    private List<ReviewCriterion> synchronizeCriteria(
            ReviewRound round,
            List<ReviewCriterion> existingCriteria,
            List<CriterionPlan> plans
    ) {
        List<ReviewCriterion> activeCriteria = new ArrayList<>();
        Set<Long> retainedCriterionIds = new HashSet<>();

        for (CriterionPlan plan : plans) {
            ReviewCriterion criterion = plan.criterion();
            if (criterion == null) {
                criterion = ReviewCriterion.builder()
                        .reviewRound(round)
                        .code(plan.code())
                        .label(plan.label())
                        .maxScore(plan.maxScore())
                        .sortOrder(plan.sortOrder())
                        .active(true)
                        .build();
            } else {
                criterion.update(
                        plan.label(),
                        plan.maxScore(),
                        plan.sortOrder());
                criterion.activate();
                retainedCriterionIds.add(criterion.getId());
            }
            activeCriteria.add(criterion);
        }

        for (ReviewCriterion criterion : existingCriteria) {
            if (!retainedCriterionIds.contains(criterion.getId())) {
                criterion.deactivate();
            }
        }

        List<ReviewCriterion> criteriaToSave =
                new ArrayList<>(existingCriteria);
        activeCriteria.stream()
                .filter(criterion -> criterion.getId() == null)
                .forEach(criteriaToSave::add);
        if (!criteriaToSave.isEmpty()) {
            reviewCriterionRepository.saveAll(criteriaToSave);
        }
        return activeCriteria(activeCriteria);
    }

    private Map<Long, List<ReviewCriterion>> groupCriteriaByRoundId(
            List<ReviewCriterion> criteria
    ) {
        Map<Long, List<ReviewCriterion>> grouped = new HashMap<>();
        for (ReviewCriterion criterion : criteria) {
            grouped.computeIfAbsent(
                    criterion.getReviewRound().getId(),
                    key -> new ArrayList<>()).add(criterion);
        }
        return grouped;
    }

    private List<ReviewCriterion> activeCriteria(
            List<ReviewCriterion> criteria
    ) {
        return criteria.stream()
                .filter(ReviewCriterion::isActive)
                .sorted(Comparator
                        .comparingInt(ReviewCriterion::getSortOrder)
                        .thenComparing(
                                ReviewCriterion::getId,
                                Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private String codeKey(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private record CriterionPlan(
            ReviewCriterion criterion,
            String code,
            String label,
            int maxScore,
            int sortOrder
    ) {
    }
}
