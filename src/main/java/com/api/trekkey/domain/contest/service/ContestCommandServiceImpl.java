package com.api.trekkey.domain.contest.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.contest.entity.StageStatus;
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
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ReviewCriterionRepository reviewCriterionRepository;
    private final ContestHtmlSanitizer contestHtmlSanitizer;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    public ContestDetailRes createContest(Long userId, ContestCreateReq req) {
        User user = findUser(userId);

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

        List<StageRes> stageResList = saveStages(contest, sortBySequenceNo(req.stages()));

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

        List<ContestStage> existingStages =
                contestStageRepository.findAllByContestIdOrderBySequenceNoAsc(contest.getId());
        Map<Long, ContestStage> existingById = new HashMap<>();
        existingStages.forEach(stage -> existingById.put(stage.getId(), stage));

        // criteria는 전량 교체(유지되는 단계 포함 전체 삭제 후 재삽입 — 단순성 우선)
        // TODO: REVIEW 도메인 구현 후 심사 기록 있는 단계 삭제 시 STAGE_HAS_REVIEWS 가드 추가
        if (!existingStages.isEmpty()) {
            reviewCriterionRepository.deleteByContestStageIdIn(
                    existingStages.stream().map(ContestStage::getId).toList());
            reviewCriterionRepository.flush();
        }

        List<StageReq> orderedStages = sortBySequenceNo(req.stages());
        List<Long> requestedStageIds = orderedStages.stream()
                .map(StageReq::id)
                .filter(Objects::nonNull)
                .toList();

        // 요청에 없는 기존 단계는 삭제 (criteria는 위에서 이미 전량 삭제됨)
        List<ContestStage> stagesToDelete = existingStages.stream()
                .filter(stage -> !requestedStageIds.contains(stage.getId()))
                .toList();
        if (!stagesToDelete.isEmpty()) {
            contestStageRepository.deleteAll(stagesToDelete);
            contestStageRepository.flush();
        }

        List<StageRes> stageResList = new ArrayList<>();
        for (int i = 0; i < orderedStages.size(); i++) {
            StageReq stageReq = orderedStages.get(i);
            int sequenceNo = i + 1;

            ContestStage stage;
            if (stageReq.id() != null) {
                stage = existingById.get(stageReq.id());
                if (stage == null) {
                    throw new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND);
                }
                stage.update(
                        stageReq.name(),
                        stageReq.stageType(),
                        sequenceNo,
                        stageReq.status(),
                        stageReq.startsAt(),
                        stageReq.endsAt(),
                        stageReq.targetType(),
                        stageReq.passRule(),
                        stageReq.passCount(),
                        stageReq.minScore()
                );
            } else {
                stage = contestStageRepository.save(buildStage(contest, stageReq, sequenceNo));
            }

            List<ReviewCriterion> criteria = saveCriteria(stage, stageReq.criteria());
            stageResList.add(StageRes.from(stage, toCriterionResList(criteria)));
        }

        adminAuditLogger.log(user.getId(), user.getOrganization().getId(), AuditAction.CONTEST_UPDATE,
                TARGET_TYPE_CONTEST, contest.getId(), contest.getTitle());

        return ContestDetailRes.of(contest, stageResList);
    }

    @Override
    public StageRes updateStageStatus(Long userId, Long stageId, StageStatusUpdateReq req) {
        User user = findUser(userId);

        ContestStage stage = contestStageRepository.findById(stageId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND));

        validateSameOrganization(stage.getContest(), user);

        StageStatus previousStatus = stage.getStatus();
        stage.changeStatus(req.status());

        adminAuditLogger.log(user.getId(), user.getOrganization().getId(), AuditAction.STAGE_STATUS_CHANGE,
                TARGET_TYPE_STAGE, stageId, "status: " + previousStatus + "→" + req.status());

        List<ReviewCriterion> criteria =
                reviewCriterionRepository.findAllByContestStageIdInOrderBySortOrderAsc(List.of(stageId));

        return StageRes.from(stage, toCriterionResList(criteria));
    }

    //======= 헬퍼 메서드 ==========

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }

    private void validateSameOrganization(Contest contest, User user) {
        if (!contest.getOrganization().getId().equals(user.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    private List<StageReq> sortBySequenceNo(List<StageReq> stages) {
        return stages.stream()
                .sorted(Comparator.comparing(StageReq::sequenceNo))
                .toList();
    }

    private List<StageRes> saveStages(Contest contest, List<StageReq> orderedStages) {
        List<StageRes> stageResList = new ArrayList<>();
        for (int i = 0; i < orderedStages.size(); i++) {
            StageReq stageReq = orderedStages.get(i);
            ContestStage stage = contestStageRepository.save(buildStage(contest, stageReq, i + 1));
            List<ReviewCriterion> criteria = saveCriteria(stage, stageReq.criteria());
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

    private List<ReviewCriterion> saveCriteria(ContestStage stage, List<CriterionReq> criterionReqs) {
        if (criterionReqs == null || criterionReqs.isEmpty()) {
            return List.of();
        }

        List<ReviewCriterion> criteria = new ArrayList<>();
        for (int i = 0; i < criterionReqs.size(); i++) {
            CriterionReq criterionReq = criterionReqs.get(i);

            String code = (criterionReq.code() == null || criterionReq.code().isBlank())
                    ? "criterion-" + (i + 1)
                    : criterionReq.code();
            int sortOrder = criterionReq.sortOrder() == null ? i + 1 : criterionReq.sortOrder();

            criteria.add(ReviewCriterion.builder()
                    .contestStage(stage)
                    .code(code)
                    .label(criterionReq.label())
                    .maxScore(criterionReq.maxScore())
                    .sortOrder(sortOrder)
                    .active(true)
                    .build());
        }

        return reviewCriterionRepository.saveAll(criteria);
    }

    private List<CriterionRes> toCriterionResList(List<ReviewCriterion> criteria) {
        return criteria.stream()
                .map(CriterionRes::from)
                .toList();
    }
}
