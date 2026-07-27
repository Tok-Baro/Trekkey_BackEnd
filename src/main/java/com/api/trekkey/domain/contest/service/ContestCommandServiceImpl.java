package com.api.trekkey.domain.contest.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.contest.support.ContestHtmlSanitizer;
import com.api.trekkey.domain.contest.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.CriterionReq;
import com.api.trekkey.domain.contest.web.dto.CriterionRes;
import com.api.trekkey.domain.contest.web.dto.StageReq;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.contest.web.dto.StageStatusUpdateReq;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContestCommandServiceImpl implements ContestCommandService {

    private static final String TARGET_TYPE_CONTEST = "CONTEST";
    private static final String TARGET_TYPE_STAGE = "STAGE";
    private static final String CRITERION_CODE_REGEX = "[A-Za-z0-9][A-Za-z0-9_-]*";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final ReviewCriterionRepository reviewCriterionRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final EntityManager entityManager;
    private final ContestHtmlSanitizer contestHtmlSanitizer;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    public ContestDetailRes createContest(Long userId, ContestCreateReq req) {
        User user = findUser(userId);
        List<StageReq> orderedStages = sortBySequenceNo(req.stages());
        validateSubmissionStageCount(orderedStages);
        List<StagePlan> stagePlans = planNewStages(orderedStages);

        Contest contest = contestRepository.save(Contest.builder()
                .organization(user.getOrganization())
                .ownerUser(user)
                .title(req.title())
                .department(req.department())
                .status(req.status())
                .participationType(req.participationType())
                .awardCount(req.awardCount())
                .posterUrl(req.posterUrl())
                .summary(req.summary())
                .target(req.target())
                .applicationMethod(req.applicationMethod())
                .benefits(req.benefits())
                .tags(req.tags())
                .detailHtml(contestHtmlSanitizer.sanitize(req.detailHtml()))
                .build());

        List<StageRes> stageResList = saveNewStages(contest, stagePlans);

        adminAuditLogger.log(user.getId(), user.getOrganization().getId(), AuditAction.CONTEST_CREATE,
                TARGET_TYPE_CONTEST, contest.getId(), contest.getTitle());

        return ContestDetailRes.of(contest, stageResList);
    }

    @Override
    public ContestDetailRes updateContest(Long userId, String publicId, ContestCreateReq req) {
        User user = findUser(userId);

        Contest contest = contestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));

        validateSameOrganization(contest, user);

        List<ContestStage> existingStages =
                contestStageRepository.findAllForUpdateByContestIdOrderBySequenceNoAsc(contest.getId());
        Map<Long, ContestStage> existingStagesById = new HashMap<>();
        existingStages.forEach(stage -> existingStagesById.put(stage.getId(), stage));

        List<ReviewCriterion> existingCriteria = findCriteria(existingStages);
        Map<Long, List<ReviewCriterion>> existingCriteriaByStageId = groupCriteriaByStageId(existingCriteria);
        Set<Long> stagesWithReviewEntries = findStagesWithReviewEntries(
                existingStages);

        List<StageReq> orderedStages = sortBySequenceNo(req.stages());
        validateSubmissionStageCount(orderedStages);
        List<StagePlan> stagePlans = planStageUpdate(
                orderedStages,
                existingStages,
                existingStagesById,
                existingCriteriaByStageId,
                stagesWithReviewEntries
        );
        Set<Long> requestedStageIds = new HashSet<>();
        stagePlans.stream()
                .map(StagePlan::stage)
                .filter(Objects::nonNull)
                .map(ContestStage::getId)
                .forEach(requestedStageIds::add);

        List<ContestStage> stagesToDelete = existingStages.stream()
                .filter(stage -> !requestedStageIds.contains(stage.getId()))
                .toList();

        contest.update(
                req.title(),
                req.department(),
                req.status(),
                req.participationType(),
                req.awardCount(),
                req.posterUrl(),
                req.summary(),
                req.target(),
                req.applicationMethod(),
                req.benefits(),
                req.tags(),
                contestHtmlSanitizer.sanitize(req.detailHtml())
        );

        if (!stagesToDelete.isEmpty()) {
            reviewCriterionRepository.deleteByContestStageIdIn(
                    stagesToDelete.stream().map(ContestStage::getId).toList());
            reviewCriterionRepository.flush();
            contestStageRepository.deleteAll(stagesToDelete);
            contestStageRepository.flush();
        }

        moveChangedStagesToTemporarySequences(stagePlans, existingStages);

        List<StageRes> stageResList = new ArrayList<>();
        for (StagePlan plan : stagePlans) {
            ContestStage stage = plan.stage();
            List<ReviewCriterion> criteria;

            if (stage == null) {
                stage = contestStageRepository.save(buildStage(contest, plan.request(), plan.sequenceNo()));
                criteria = synchronizeCriteria(stage, List.of(), plan.criteria());
            } else if (stage.isConfigurationEditable()) {
                StageReq stageReq = plan.request();
                stage.updateConfiguration(
                        stageReq.name(),
                        stageReq.stageType(),
                        plan.sequenceNo(),
                        stageReq.startsAt(),
                        stageReq.endsAt(),
                        stageReq.targetType(),
                        stageReq.passRule(),
                        stageReq.passCount(),
                        stageReq.minScore()
                );
                criteria = synchronizeCriteria(
                        stage,
                        existingCriteriaByStageId.getOrDefault(stage.getId(), List.of()),
                        plan.criteria()
                );
            } else {
                criteria = activeCriteria(
                        existingCriteriaByStageId.getOrDefault(stage.getId(), List.of()));
            }

            stageResList.add(StageRes.from(stage, toCriterionResList(criteria)));
        }

        adminAuditLogger.log(user.getId(), user.getOrganization().getId(), AuditAction.CONTEST_UPDATE,
                TARGET_TYPE_CONTEST, contest.getId(), contest.getTitle());

        return ContestDetailRes.of(contest, stageResList);
    }

    @Override
    public StageRes updateStageStatus(Long userId, Long stageId, StageStatusUpdateReq req) {
        User user = findUser(userId);

        validateStageOrganization(stageId, user);
        ContestStage stage = contestStageRepository.findByIdForUpdate(stageId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND));

        StageStatus previousStatus = stage.getStatus();
        StageStatus nextStatus = req.status();
        if (!previousStatus.canTransitionTo(nextStatus)) {
            throw new CustomException(ContestErrorResponseCode.INVALID_STAGE_STATUS_TRANSITION);
        }

        List<ReviewCriterion> criteria =
                reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                        List.of(stageId));
        List<ReviewCriterion> activeCriteria = activeCriteria(criteria);
        List<ReviewRoundEntry> reviewEntries = List.of();

        if (previousStatus == StageStatus.PREPARING
                && nextStatus == StageStatus.OPEN) {
            if (!stage.hasValidConfigurationForOpening()) {
                throw new CustomException(ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);
            }
            if (stage.getStageType().supportsReviewCriteria() && activeCriteria.isEmpty()) {
                throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_REQUIRED);
            }
            if (activeCriteria.stream().anyMatch(criterion -> criterion.getMaxScore() < 1)) {
                throw new CustomException(ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);
            }
            if (stage.getStageType().supportsReviewCriteria()) {
                reviewEntries = reviewRoundEntryRepository
                        .findAllForUpdateByReviewStageIdOrderByIdAsc(
                                stageId);
                if (reviewEntries.isEmpty()) {
                    throw new CustomException(
                            ReviewErrorResponseCode.REVIEW_ENTRY_REQUIRED);
                }
                Contest contest = stage.getContest();
                entityManager.refresh(
                        contest,
                        LockModeType.PESSIMISTIC_READ
                );
                if (contest.getStatus() != ContestStatus.REVIEWING) {
                    throw new CustomException(
                            ReviewErrorResponseCode
                                    .REVIEW_ENTRY_CONTEST_NOT_REVIEWING);
                }
            }
        }

        if (previousStatus != nextStatus) {
            stage.changeStatus(nextStatus);
            if (previousStatus == StageStatus.PREPARING
                    && nextStatus == StageStatus.OPEN
                    && stage.getStageType().supportsReviewCriteria()) {
                reviewEntries.forEach(ReviewRoundEntry::startReview);
            }
            adminAuditLogger.log(
                    user.getId(),
                    user.getOrganization().getId(),
                    AuditAction.STAGE_STATUS_CHANGE,
                    TARGET_TYPE_STAGE,
                    stageId,
                    "status: " + previousStatus + "→" + nextStatus
            );
        }

        return StageRes.from(stage, toCriterionResList(activeCriteria));
    }

    //======= 헬퍼 메서드 ==========

    private User findUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
    }

    private void validateSameOrganization(Contest contest, User user) {
        if (!contest.getOrganization().getId().equals(user.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private void validateStageOrganization(Long stageId, User user) {
        Long organizationId = contestStageRepository.findOrganizationIdById(stageId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND));
        if (!organizationId.equals(user.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private List<StageReq> sortBySequenceNo(List<StageReq> stages) {
        return stages.stream()
                .sorted(Comparator.comparing(StageReq::sequenceNo))
                .toList();
    }

    private void validateSubmissionStageCount(List<StageReq> stages) {
        long submissionStageCount = stages.stream()
                .filter(stage -> stage.stageType() == StageType.SUBMISSION)
                .count();
        if (submissionStageCount > 1) {
            throw new CustomException(
                    ContestErrorResponseCode.SUBMISSION_STAGE_DUPLICATED);
        }
    }

    private List<StagePlan> planNewStages(List<StageReq> orderedStages) {
        List<StagePlan> plans = new ArrayList<>();
        for (int i = 0; i < orderedStages.size(); i++) {
            StageReq stageReq = orderedStages.get(i);
            validateNewStage(stageReq);
            plans.add(new StagePlan(
                    stageReq,
                    null,
                    i + 1,
                    resolveCriterionPlans(stageReq, List.of())
            ));
        }
        return plans;
    }

    private List<StagePlan> planStageUpdate(
            List<StageReq> orderedStages,
            List<ContestStage> existingStages,
            Map<Long, ContestStage> existingStagesById,
            Map<Long, List<ReviewCriterion>> existingCriteriaByStageId,
            Set<Long> stagesWithReviewEntries
    ) {
        List<StagePlan> plans = new ArrayList<>();
        Set<Long> requestedStageIds = new HashSet<>();

        for (int i = 0; i < orderedStages.size(); i++) {
            StageReq stageReq = orderedStages.get(i);
            int sequenceNo = i + 1;

            if (stageReq.id() == null) {
                validateNewStage(stageReq);
                plans.add(new StagePlan(
                        stageReq,
                        null,
                        sequenceNo,
                        resolveCriterionPlans(stageReq, List.of())
                ));
                continue;
            }

            if (!requestedStageIds.add(stageReq.id())) {
                throw new CustomException(ContestErrorResponseCode.STAGE_DUPLICATED);
            }

            ContestStage stage = existingStagesById.get(stageReq.id());
            if (stage == null) {
                throw new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND);
            }
            if (stage.getStatus() != stageReq.status()) {
                throw new CustomException(ContestErrorResponseCode.INVALID_STAGE_STATUS_TRANSITION);
            }

            List<ReviewCriterion> existingCriteria =
                    existingCriteriaByStageId.getOrDefault(stage.getId(), List.of());
            List<CriterionPlan> criterionPlans = resolveCriterionPlans(stageReq, existingCriteria);

            if (isStageConfigurationLocked(stage, stagesWithReviewEntries)
                    && (!stage.hasSameConfiguration(
                    stageReq.name(),
                    stageReq.stageType(),
                    sequenceNo,
                    stageReq.startsAt(),
                    stageReq.endsAt(),
                    stageReq.targetType(),
                    stageReq.passRule(),
                    stageReq.passCount(),
                    stageReq.minScore())
                    || !hasSameActiveCriteria(existingCriteria, criterionPlans))) {
                throw new CustomException(ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);
            }

            plans.add(new StagePlan(stageReq, stage, sequenceNo, criterionPlans));
        }

        boolean removesLockedStage = existingStages.stream()
                .filter(stage -> !requestedStageIds.contains(stage.getId()))
                .anyMatch(stage -> isStageConfigurationLocked(
                        stage,
                        stagesWithReviewEntries));
        if (removesLockedStage) {
            throw new CustomException(ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);
        }

        return plans;
    }

    private void validateNewStage(StageReq stageReq) {
        if (stageReq.id() != null) {
            throw new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND);
        }
        if (stageReq.status() != StageStatus.PREPARING) {
            throw new CustomException(ContestErrorResponseCode.INVALID_STAGE_STATUS_TRANSITION);
        }
    }

    private List<StageRes> saveNewStages(Contest contest, List<StagePlan> stagePlans) {
        List<StageRes> stageResList = new ArrayList<>();
        for (StagePlan plan : stagePlans) {
            ContestStage stage =
                    contestStageRepository.save(buildStage(contest, plan.request(), plan.sequenceNo()));
            List<ReviewCriterion> criteria = synchronizeCriteria(stage, List.of(), plan.criteria());
            stageResList.add(StageRes.from(stage, toCriterionResList(criteria)));
        }
        return stageResList;
    }

    private ContestStage buildStage(Contest contest, StageReq stageReq, int sequenceNo) {
        return ContestStage.builder()
                .contest(contest)
                .name(stageReq.name())
                .stageType(stageReq.stageType())
                .sequenceNo(sequenceNo)
                .status(stageReq.status())
                .startsAt(stageReq.startsAt())
                .endsAt(stageReq.endsAt())
                .targetType(stageReq.targetType())
                .passRule(stageReq.passRule())
                .passCount(stageReq.passCount())
                .minScore(stageReq.minScore())
                .build();
    }

    private List<ReviewCriterion> findCriteria(List<ContestStage> stages) {
        if (stages.isEmpty()) {
            return List.of();
        }
        return reviewCriterionRepository.findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
                stages.stream().map(ContestStage::getId).toList());
    }

    private Set<Long> findStagesWithReviewEntries(
            List<ContestStage> stages
    ) {
        if (stages.isEmpty()) {
            return Set.of();
        }
        return reviewRoundEntryRepository
                .findStagesWithEntriesForShare(
                        stages.stream().map(ContestStage::getId).toList())
                .stream()
                .map(ContestStage::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean isStageConfigurationLocked(
            ContestStage stage,
            Set<Long> stagesWithReviewEntries
    ) {
        return !stage.isConfigurationEditable()
                || stagesWithReviewEntries.contains(stage.getId());
    }

    private Map<Long, List<ReviewCriterion>> groupCriteriaByStageId(List<ReviewCriterion> criteria) {
        Map<Long, List<ReviewCriterion>> grouped = new HashMap<>();
        for (ReviewCriterion criterion : criteria) {
            grouped.computeIfAbsent(criterion.getContestStage().getId(), key -> new ArrayList<>())
                    .add(criterion);
        }
        return grouped;
    }

    private List<CriterionPlan> resolveCriterionPlans(
            StageReq stageReq,
            List<ReviewCriterion> existingCriteria
    ) {
        List<CriterionReq> criterionReqs =
                stageReq.criteria() == null ? List.of() : stageReq.criteria();
        if (!criterionReqs.isEmpty() && !stageReq.stageType().supportsReviewCriteria()) {
            throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_NOT_ALLOWED);
        }

        Map<Long, ReviewCriterion> existingById = new HashMap<>();
        Map<String, ReviewCriterion> existingByCode = new HashMap<>();
        for (ReviewCriterion criterion : existingCriteria) {
            existingById.put(criterion.getId(), criterion);
            existingByCode.put(codeKey(criterion.getCode()), criterion);
        }

        List<CriterionPlan> plans = new ArrayList<>();
        Set<Long> selectedCriterionIds = new HashSet<>();
        Set<String> selectedCodes = new HashSet<>();

        for (int i = 0; i < criterionReqs.size(); i++) {
            CriterionReq criterionReq = criterionReqs.get(i);
            validateCriterion(criterionReq);

            String requestedCode = normalizeCode(criterionReq.code());
            ReviewCriterion criterion;
            String code;

            if (criterionReq.id() != null) {
                criterion = existingById.get(criterionReq.id());
                if (criterion == null) {
                    throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_NOT_FOUND);
                }
                if (requestedCode != null
                        && !codeKey(criterion.getCode()).equals(codeKey(requestedCode))) {
                    throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_CODE_IMMUTABLE);
                }
                code = criterion.getCode();
            } else {
                code = requestedCode == null
                        ? "criterion-" + (i + 1)
                        : requestedCode;
                criterion = existingByCode.get(codeKey(code));
                if (criterion != null) {
                    code = criterion.getCode();
                }
            }

            if (criterion != null && !selectedCriterionIds.add(criterion.getId())) {
                throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_DUPLICATED);
            }
            if (!selectedCodes.add(codeKey(code))) {
                throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_DUPLICATED);
            }
            if (criterionReq.maxScore() == 0
                    && (criterion == null || criterion.getMaxScore() != 0)) {
                throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_INVALID);
            }

            int sortOrder =
                    criterionReq.sortOrder() == null ? i + 1 : criterionReq.sortOrder();
            plans.add(new CriterionPlan(
                    criterion,
                    code,
                    normalizeLabel(criterion, criterionReq.label()),
                    criterionReq.maxScore(),
                    sortOrder
            ));
        }
        return plans;
    }

    private void validateCriterion(CriterionReq criterionReq) {
        String code = normalizeCode(criterionReq.code());
        if (criterionReq.label() == null
                || criterionReq.label().isBlank()
                || criterionReq.label().trim().length() > 60
                || criterionReq.maxScore() == null
                || criterionReq.maxScore() < 0
                || (criterionReq.sortOrder() != null && criterionReq.sortOrder() < 1)
                || (code != null
                && (code.length() > 60 || !code.matches(CRITERION_CODE_REGEX)))) {
            throw new CustomException(ContestErrorResponseCode.REVIEW_CRITERION_INVALID);
        }
    }

    private List<ReviewCriterion> synchronizeCriteria(
            ContestStage stage,
            List<ReviewCriterion> existingCriteria,
            List<CriterionPlan> plans
    ) {
        List<ReviewCriterion> activeCriteria = new ArrayList<>();
        Set<Long> retainedCriterionIds = new HashSet<>();

        for (CriterionPlan plan : plans) {
            ReviewCriterion criterion = plan.criterion();
            if (criterion == null) {
                criterion = ReviewCriterion.builder()
                        .contestStage(stage)
                        .code(plan.code())
                        .label(plan.label())
                        .maxScore(plan.maxScore())
                        .sortOrder(plan.sortOrder())
                        .active(true)
                        .build();
            } else {
                criterion.update(plan.label(), plan.maxScore(), plan.sortOrder());
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

        List<ReviewCriterion> criteriaToSave = new ArrayList<>(existingCriteria);
        activeCriteria.stream()
                .filter(criterion -> criterion.getId() == null)
                .forEach(criteriaToSave::add);
        if (!criteriaToSave.isEmpty()) {
            reviewCriterionRepository.saveAll(criteriaToSave);
        }

        return activeCriteria(activeCriteria);
    }

    private void moveChangedStagesToTemporarySequences(
            List<StagePlan> stagePlans,
            List<ContestStage> existingStages
    ) {
        List<StagePlan> changedPlans = stagePlans.stream()
                .filter(plan -> plan.stage() != null)
                .filter(plan -> plan.stage().isConfigurationEditable())
                .filter(plan -> plan.stage().getSequenceNo() != plan.sequenceNo())
                .toList();
        if (changedPlans.isEmpty()) {
            return;
        }

        int highestSequence = Math.max(
                stagePlans.size(),
                existingStages.stream()
                        .mapToInt(ContestStage::getSequenceNo)
                        .max()
                        .orElse(0)
        );
        int temporarySequence = highestSequence + stagePlans.size() + 1;
        for (StagePlan plan : changedPlans) {
            plan.stage().moveToSequence(temporarySequence++);
        }
        contestStageRepository.flush();
    }

    private boolean hasSameActiveCriteria(
            List<ReviewCriterion> existingCriteria,
            List<CriterionPlan> plans
    ) {
        List<ReviewCriterion> activeCriteria = activeCriteria(existingCriteria);
        if (activeCriteria.size() != plans.size()) {
            return false;
        }

        return plans.stream().allMatch(plan ->
                plan.criterion() != null
                        && plan.criterion().isActive()
                        && plan.criterion().hasSameConfiguration(
                        plan.code(),
                        plan.label(),
                        plan.maxScore(),
                        plan.sortOrder()));
    }

    private List<ReviewCriterion> activeCriteria(List<ReviewCriterion> criteria) {
        return criteria.stream()
                .filter(ReviewCriterion::isActive)
                .sorted(Comparator.comparingInt(ReviewCriterion::getSortOrder)
                        .thenComparing(ReviewCriterion::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim();
    }

    private String normalizeLabel(ReviewCriterion criterion, String label) {
        if (criterion != null && criterion.getLabel().equals(label)) {
            return label;
        }
        return label.trim();
    }

    private String codeKey(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private List<CriterionRes> toCriterionResList(List<ReviewCriterion> criteria) {
        return criteria.stream()
                .filter(ReviewCriterion::isActive)
                .map(CriterionRes::from)
                .toList();
    }

    private record StagePlan(
            StageReq request,
            ContestStage stage,
            int sequenceNo,
            List<CriterionPlan> criteria
    ) {
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
