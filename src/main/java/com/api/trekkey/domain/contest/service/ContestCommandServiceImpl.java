package com.api.trekkey.domain.contest.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.support.ContestHtmlSanitizer;
import com.api.trekkey.domain.contest.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.StageReq;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.contest.web.dto.StageStatusUpdateReq;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final ContestHtmlSanitizer contestHtmlSanitizer;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    public ContestDetailRes createContest(
            Long userId,
            ContestCreateReq req
    ) {
        User user = findUser(userId);
        List<StageReq> orderedStages = sortBySequenceNo(req.stages());
        validateSubmissionStageCount(orderedStages);
        orderedStages.forEach(this::validateNewStage);

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

        List<StageRes> stageResponses = new ArrayList<>();
        for (int index = 0; index < orderedStages.size(); index++) {
            ContestStage stage = contestStageRepository.save(buildStage(
                    contest,
                    orderedStages.get(index),
                    index + 1
            ));
            stageResponses.add(StageRes.from(stage, List.of()));
        }

        adminAuditLogger.log(
                user.getId(),
                user.getOrganization().getId(),
                AuditAction.CONTEST_CREATE,
                TARGET_TYPE_CONTEST,
                contest.getId(),
                contest.getTitle()
        );
        return ContestDetailRes.of(contest, stageResponses);
    }

    @Override
    public ContestDetailRes updateContest(
            Long userId,
            String publicId,
            ContestCreateReq req
    ) {
        User user = findUser(userId);
        Contest contest = contestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.CONTEST_NOT_FOUND));
        validateSameOrganization(contest, user);

        List<ContestStage> existingStages = contestStageRepository
                .findAllForUpdateByContestIdOrderBySequenceNoAsc(
                        contest.getId());
        Map<Long, ContestStage> existingById = new HashMap<>();
        existingStages.forEach(stage ->
                existingById.put(stage.getId(), stage));

        List<StageReq> orderedStages = sortBySequenceNo(req.stages());
        validateSubmissionStageCount(orderedStages);
        List<StagePlan> plans = planStageUpdate(
                orderedStages,
                existingStages,
                existingById
        );
        Set<Long> requestedIds = new HashSet<>();
        plans.stream()
                .map(StagePlan::stage)
                .filter(Objects::nonNull)
                .map(ContestStage::getId)
                .forEach(requestedIds::add);

        List<ContestStage> stagesToDelete = existingStages.stream()
                .filter(stage -> !requestedIds.contains(stage.getId()))
                .toList();
        if (stagesToDelete.stream()
                .anyMatch(stage -> !stage.isConfigurationEditable())) {
            throw new CustomException(
                    ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);
        }

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
            contestStageRepository.deleteAll(stagesToDelete);
            contestStageRepository.flush();
        }
        moveChangedStagesToTemporarySequences(plans, existingStages);

        List<StageRes> stageResponses = new ArrayList<>();
        for (StagePlan plan : plans) {
            ContestStage stage = plan.stage();
            if (stage == null) {
                stage = contestStageRepository.save(buildStage(
                        contest,
                        plan.request(),
                        plan.sequenceNo()
                ));
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
            }
            stageResponses.add(StageRes.from(stage, List.of()));
        }

        adminAuditLogger.log(
                user.getId(),
                user.getOrganization().getId(),
                AuditAction.CONTEST_UPDATE,
                TARGET_TYPE_CONTEST,
                contest.getId(),
                contest.getTitle()
        );
        return ContestDetailRes.of(contest, stageResponses);
    }

    @Override
    public StageRes updateStageStatus(
            Long userId,
            Long stageId,
            StageStatusUpdateReq req
    ) {
        User user = findUser(userId);
        validateStageOrganization(stageId, user);
        ContestStage stage = contestStageRepository
                .findByIdForUpdate(stageId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));

        if (stage.getStageType().supportsReviewCriteria()) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_REQUIRED);
        }

        StageStatus previousStatus = stage.getStatus();
        StageStatus nextStatus = req.status();
        if (!previousStatus.canTransitionTo(nextStatus)) {
            throw new CustomException(
                    ContestErrorResponseCode
                            .INVALID_STAGE_STATUS_TRANSITION);
        }
        if (previousStatus == StageStatus.PREPARING
                && nextStatus == StageStatus.OPEN
                && !stage.hasValidConfigurationForOpening()) {
            throw new CustomException(
                    ContestErrorResponseCode.STAGE_CONFIGURATION_INVALID);
        }

        if (previousStatus != nextStatus) {
            stage.changeStatus(nextStatus);
            adminAuditLogger.log(
                    user.getId(),
                    user.getOrganization().getId(),
                    AuditAction.STAGE_STATUS_CHANGE,
                    TARGET_TYPE_STAGE,
                    stageId,
                    "status: " + previousStatus + "→" + nextStatus
            );
        }
        return StageRes.from(stage, List.of());
    }

    private User findUser(Long userId) {
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

    private void validateSameOrganization(Contest contest, User user) {
        if (!contest.getOrganization().getId()
                .equals(user.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private void validateStageOrganization(Long stageId, User user) {
        Long organizationId = contestStageRepository
                .findOrganizationIdById(stageId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND));
        if (!organizationId.equals(user.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private List<StageReq> sortBySequenceNo(List<StageReq> stages) {
        return stages.stream()
                .sorted(Comparator.comparing(StageReq::sequenceNo))
                .toList();
    }

    private void validateSubmissionStageCount(List<StageReq> stages) {
        long submissionStageCount = stages.stream()
                .filter(stage -> stage.stageType()
                        == StageType.SUBMISSION)
                .count();
        if (submissionStageCount > 1) {
            throw new CustomException(
                    ContestErrorResponseCode
                            .SUBMISSION_STAGE_DUPLICATED);
        }
    }

    private void validateNewStage(StageReq stageReq) {
        validateStageRequest(stageReq);
        if (stageReq.id() != null) {
            throw new CustomException(
                    ContestErrorResponseCode.STAGE_NOT_FOUND);
        }
        if (stageReq.status() != StageStatus.PREPARING) {
            throw new CustomException(
                    ContestErrorResponseCode
                            .INVALID_STAGE_STATUS_TRANSITION);
        }
    }

    private void validateStageRequest(StageReq stageReq) {
        if (stageReq.stageType().supportsReviewCriteria()
                || (stageReq.criteria() != null
                && !stageReq.criteria().isEmpty())) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_REQUIRED);
        }
    }

    private List<StagePlan> planStageUpdate(
            List<StageReq> orderedStages,
            List<ContestStage> existingStages,
            Map<Long, ContestStage> existingById
    ) {
        List<StagePlan> plans = new ArrayList<>();
        Set<Long> requestedIds = new HashSet<>();
        for (int index = 0; index < orderedStages.size(); index++) {
            StageReq request = orderedStages.get(index);
            validateStageRequest(request);
            int sequenceNo = index + 1;
            if (request.id() == null) {
                validateNewStage(request);
                plans.add(new StagePlan(request, null, sequenceNo));
                continue;
            }
            if (!requestedIds.add(request.id())) {
                throw new CustomException(
                        ContestErrorResponseCode.STAGE_DUPLICATED);
            }
            ContestStage stage = existingById.get(request.id());
            if (stage == null) {
                throw new CustomException(
                        ContestErrorResponseCode.STAGE_NOT_FOUND);
            }
            if (stage.getStatus() != request.status()) {
                throw new CustomException(
                        ContestErrorResponseCode
                                .INVALID_STAGE_STATUS_TRANSITION);
            }
            if (!stage.isConfigurationEditable()
                    && !stage.hasSameConfiguration(
                    request.name(),
                    request.stageType(),
                    sequenceNo,
                    request.startsAt(),
                    request.endsAt(),
                    request.targetType(),
                    request.passRule(),
                    request.passCount(),
                    request.minScore())) {
                throw new CustomException(
                        ContestErrorResponseCode
                                .STAGE_CONFIGURATION_LOCKED);
            }
            plans.add(new StagePlan(request, stage, sequenceNo));
        }

        boolean removesLockedStage = existingStages.stream()
                .filter(stage -> !requestedIds.contains(stage.getId()))
                .anyMatch(stage -> !stage.isConfigurationEditable());
        if (removesLockedStage) {
            throw new CustomException(
                    ContestErrorResponseCode.STAGE_CONFIGURATION_LOCKED);
        }
        return plans;
    }

    private ContestStage buildStage(
            Contest contest,
            StageReq stageReq,
            int sequenceNo
    ) {
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

    private void moveChangedStagesToTemporarySequences(
            List<StagePlan> stagePlans,
            List<ContestStage> existingStages
    ) {
        List<StagePlan> changedPlans = stagePlans.stream()
                .filter(plan -> plan.stage() != null)
                .filter(plan -> plan.stage().isConfigurationEditable())
                .filter(plan -> plan.stage().getSequenceNo()
                        != plan.sequenceNo())
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
        int temporarySequence =
                highestSequence + stagePlans.size() + 1;
        for (StagePlan plan : changedPlans) {
            plan.stage().moveToSequence(temporarySequence++);
        }
        contestStageRepository.flush();
    }

    private record StagePlan(
            StageReq request,
            ContestStage stage,
            int sequenceNo
    ) {
    }
}
