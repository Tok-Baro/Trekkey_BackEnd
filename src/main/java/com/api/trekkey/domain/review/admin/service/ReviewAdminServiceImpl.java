package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.review.admin.web.dto.EntryDecisionReq;
import com.api.trekkey.domain.review.admin.web.dto.EntryRes;
import com.api.trekkey.domain.review.admin.web.dto.JudgeCreateReq;
import com.api.trekkey.domain.review.admin.web.dto.JudgeRes;
import com.api.trekkey.domain.review.entity.AssignmentStatus;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.EntryStatus;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ContestStageEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRepository;
import com.api.trekkey.domain.review.support.ReviewTokenSupport;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewAdminServiceImpl implements ReviewAdminService {

    private static final int TOKEN_VALID_DAYS = 30;
    private static final String TARGET_TYPE_JUDGE = "JUDGE";
    private static final String TARGET_TYPE_STAGE = "STAGE";
    private static final String TARGET_TYPE_ENTRY = "ENTRY";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final SubmissionRepository submissionRepository;
    private final ContestJudgeRepository contestJudgeRepository;
    private final ContestStageEntryRepository entryRepository;
    private final ReviewAssignmentRepository assignmentRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewTokenSupport reviewTokenSupport;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    @Transactional
    public JudgeRes createJudge(Long adminUserId, String contestPublicId, JudgeCreateReq request) {
        User admin = findAdmin(adminUserId);
        Contest contest = findContestInOrganization(contestPublicId, admin);

        String rawToken = reviewTokenSupport.generateToken();
        ContestJudge judge = contestJudgeRepository.save(ContestJudge.builder()
                .contest(contest)
                .name(request.name().trim())
                .roleLabel(request.roleLabel().trim())
                .reviewTokenHash(reviewTokenSupport.hash(rawToken))
                .tokenExpiresAt(LocalDateTime.now().plusDays(TOKEN_VALID_DAYS))
                .build());

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.JUDGE_INVITE,
                TARGET_TYPE_JUDGE, judge.getId(), judge.getName());

        //심사 링크 원문은 이 응답에만 노출한다 — 저장·로그 금지
        return JudgeRes.of(judge, 0, 0, reviewTokenSupport.buildReviewUrl(rawToken));
    }

    @Override
    public List<JudgeRes> getJudges(Long adminUserId, String contestPublicId) {
        User admin = findAdmin(adminUserId);
        Contest contest = findContestInOrganization(contestPublicId, admin);

        List<ContestJudge> judges = contestJudgeRepository.findAllByContestIdOrderByCreatedAtDesc(contest.getId());
        if (judges.isEmpty()) {
            return List.of();
        }

        // 배정/완료 집계 일괄 조회
        Map<Long, long[]> countsByJudgeId = new HashMap<>();
        assignmentRepository.countByJudgeIds(judges.stream().map(ContestJudge::getId).toList())
                .forEach(row -> countsByJudgeId.put(
                        (Long) row[0], new long[]{(Long) row[1], ((Number) row[2]).longValue()}));

        return judges.stream()
                .map(judge -> {
                    long[] counts = countsByJudgeId.getOrDefault(judge.getId(), new long[]{0, 0});
                    return JudgeRes.of(judge, counts[0], counts[1], null);
                })
                .toList();
    }

    @Override
    @Transactional
    public JudgeRes rotateToken(Long adminUserId, Long judgeId) {
        User admin = findAdmin(adminUserId);
        ContestJudge judge = findJudgeInOrganization(judgeId, admin);

        String rawToken = reviewTokenSupport.generateToken();
        judge.rotateToken(reviewTokenSupport.hash(rawToken), LocalDateTime.now().plusDays(TOKEN_VALID_DAYS));

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.JUDGE_INVITE,
                TARGET_TYPE_JUDGE, judge.getId(), judge.getName() + " 링크 재발급");

        return JudgeRes.of(judge, 0, 0, reviewTokenSupport.buildReviewUrl(rawToken));
    }

    @Override
    @Transactional
    public void deleteJudge(Long adminUserId, Long judgeId) {
        User admin = findAdmin(adminUserId);
        ContestJudge judge = findJudgeInOrganization(judgeId, admin);

        //배정(심사 이력)이 있으면 원장 보존을 위해 삭제를 거부한다
        if (assignmentRepository.existsByContestJudgeId(judge.getId())) {
            throw new CustomException(ReviewErrorResponseCode.JUDGE_HAS_ASSIGNMENTS);
        }

        contestJudgeRepository.delete(judge);
        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.JUDGE_DELETE,
                TARGET_TYPE_JUDGE, judgeId, judge.getName());
    }

    @Override
    @Transactional
    public List<EntryRes> openRound(Long adminUserId, Long stageId) {
        User admin = findAdmin(adminUserId);
        ContestStage stage = findReviewStageInOrganization(stageId, admin);

        if (stage.getStatus() != StageStatus.PREPARING) {
            throw new CustomException(ReviewErrorResponseCode.ROUND_ALREADY_OPENED);
        }
        List<ContestJudge> judges = contestJudgeRepository.findAllByContestId(stage.getContest().getId());
        if (judges.isEmpty()) {
            throw new CustomException(ReviewErrorResponseCode.ROUND_NO_JUDGE);
        }

        List<Submission> targets = resolveTargets(stage);
        if (targets.isEmpty()) {
            throw new CustomException(ReviewErrorResponseCode.ROUND_NO_TARGET);
        }

        LocalDateTime now = LocalDateTime.now();
        List<EntryRes> result = new ArrayList<>();
        for (Submission submission : targets) {
            //첫 심사 시작 이후 제출물 수정 금지 (erd-mvp §5)
            if (!submission.isFinalized()) {
                submission.finalizeSubmission(now);
            }
            ContestStageEntry entry = entryRepository.save(ContestStageEntry.builder()
                    .contestStage(stage)
                    .submission(submission)
                    .status(EntryStatus.IN_REVIEW)
                    .build());
            //MVP 배정 정책: 전 심사위원 × 전 대상 (erd-mvp — 배정 방식은 운영 정책으로 개방)
            for (ContestJudge judge : judges) {
                assignmentRepository.save(ReviewAssignment.builder()
                        .contestJudge(judge)
                        .contestStageEntry(entry)
                        .status(AssignmentStatus.ASSIGNED)
                        .assignedAt(now)
                        .build());
            }
            result.add(EntryRes.of(entry, 0, null));
        }

        stage.changeStatus(StageStatus.OPEN);
        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.ROUND_OPEN,
                TARGET_TYPE_STAGE, stageId, stage.getName() + " 대상 " + targets.size() + "건, 심사위원 " + judges.size() + "명");

        return result;
    }

    @Override
    public List<EntryRes> getEntries(Long adminUserId, Long stageId) {
        User admin = findAdmin(adminUserId);
        ContestStage stage = findReviewStageInOrganization(stageId, admin);

        List<ContestStageEntry> entries = entryRepository.findAllByContestStageIdOrderByIdAsc(stage.getId());
        Map<Long, List<BigDecimal>> scoresByEntryId = collectScores(entries);

        return entries.stream()
                .map(entry -> {
                    List<BigDecimal> scores = scoresByEntryId.getOrDefault(entry.getId(), List.of());
                    return EntryRes.of(entry, scores.size(), average(scores));
                })
                .toList();
    }

    @Override
    @Transactional
    public List<EntryRes> finalizeRound(Long adminUserId, Long stageId) {
        User admin = findAdmin(adminUserId);
        ContestStage stage = findReviewStageInOrganization(stageId, admin);

        if (stage.getStatus() != StageStatus.OPEN) {
            throw new CustomException(ReviewErrorResponseCode.ROUND_NOT_OPEN);
        }

        List<ContestStageEntry> entries = entryRepository.findAllByContestStageIdOrderByIdAsc(stage.getId());
        if (entries.isEmpty()) {
            throw new CustomException(ReviewErrorResponseCode.ROUND_NO_TARGET);
        }

        //집계: entry별 심사 평균 (심사 없는 entry는 0점)
        Map<Long, List<BigDecimal>> scoresByEntryId = collectScores(entries);
        Map<Long, BigDecimal> averageByEntryId = new HashMap<>();
        entries.forEach(entry -> {
            BigDecimal avg = average(scoresByEntryId.getOrDefault(entry.getId(), List.of()));
            averageByEntryId.put(entry.getId(), avg == null ? BigDecimal.ZERO : avg);
        });

        //정렬: 평균 내림차순 → 순위 부여
        List<ContestStageEntry> ranked = entries.stream()
                .sorted(Comparator.comparing((ContestStageEntry entry) ->
                        averageByEntryId.get(entry.getId())).reversed())
                .toList();

        LocalDateTime now = LocalDateTime.now();
        StagePassRule passRule = stage.getPassRule() == null ? StagePassRule.FINAL : stage.getPassRule();
        for (int i = 0; i < ranked.size(); i++) {
            ContestStageEntry entry = ranked.get(i);
            int rankNo = i + 1;
            BigDecimal score = averageByEntryId.get(entry.getId());

            if (passRule == StagePassRule.MANUAL) {
                //수동 판정 라운드 — 점수·순위만 기록하고 확정은 개별 판정(decideEntry)으로
                entry.finalizeByRule(score, rankNo, EntryStatus.ELIGIBLE, null);
            } else {
                EntryStatus status = switch (passRule) {
                    case TOP_N -> stage.getPassCount() != null && rankNo <= stage.getPassCount()
                            ? EntryStatus.PASSED : EntryStatus.FAILED;
                    case MIN_SCORE -> stage.getMinScore() != null
                            && score.compareTo(stage.getMinScore()) >= 0
                            ? EntryStatus.PASSED : EntryStatus.FAILED;
                    default -> EntryStatus.PASSED; //FINAL — 순위 확정, 수상 산출 기준 (erd-mvp)
                };
                entry.finalizeByRule(score, rankNo, status, now);
            }
        }

        stage.changeStatus(StageStatus.COMPLETED);
        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.ROUND_FINALIZE,
                TARGET_TYPE_STAGE, stageId, stage.getName() + " " + ranked.size() + "건 확정");

        return getEntriesInternal(entries, scoresByEntryId);
    }

    @Override
    @Transactional
    public EntryRes decideEntry(Long adminUserId, Long entryId, EntryDecisionReq request) {
        User admin = findAdmin(adminUserId);
        ContestStageEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new CustomException(ReviewErrorResponseCode.ENTRY_NOT_FOUND));
        validateSameOrganization(entry.getContestStage().getContest(), admin, true);

        if (entry.isFinalized()) {
            throw new CustomException(ReviewErrorResponseCode.ENTRY_ALREADY_FINALIZED);
        }

        entry.decideManually(request.status(), admin.getId(), request.reason().trim(), LocalDateTime.now());
        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.ENTRY_DECIDE,
                TARGET_TYPE_ENTRY, entryId, "status: " + request.status() + " (" + request.reason().trim() + ")");

        Map<Long, List<BigDecimal>> scores = collectScores(List.of(entry));
        List<BigDecimal> entryScores = scores.getOrDefault(entry.getId(), List.of());
        return EntryRes.of(entry, entryScores.size(), average(entryScores));
    }

    //======= 헬퍼 메서드 ==========

    private User findAdmin(Long adminUserId) {
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }

    private Contest findContestInOrganization(String contestPublicId, User admin) {
        Contest contest = contestRepository.findByPublicId(contestPublicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        validateSameOrganization(contest, admin, false);
        return contest;
    }

    private ContestJudge findJudgeInOrganization(Long judgeId, User admin) {
        ContestJudge judge = contestJudgeRepository.findById(judgeId)
                .orElseThrow(() -> new CustomException(ReviewErrorResponseCode.JUDGE_NOT_FOUND));
        if (!judge.getContest().getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(ReviewErrorResponseCode.JUDGE_NOT_FOUND);
        }
        return judge;
    }

    private ContestStage findReviewStageInOrganization(Long stageId, User admin) {
        ContestStage stage = contestStageRepository.findById(stageId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND));
        validateSameOrganization(stage.getContest(), admin, false);
        if (stage.getStageType() != StageType.REVIEW && stage.getStageType() != StageType.PRESENTATION) {
            throw new CustomException(ReviewErrorResponseCode.ROUND_NOT_REVIEW_STAGE);
        }
        return stage;
    }

    private void validateSameOrganization(Contest contest, User admin, boolean asNotFound) {
        if (!contest.getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(asNotFound
                    ? ReviewErrorResponseCode.ENTRY_NOT_FOUND
                    : ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }

    // 라운드 대상 산출 — ALL_SUBMISSIONS: 제출 완료 전체 / PREVIOUS_PASSED: 직전 확정 라운드 통과작
    private List<Submission> resolveTargets(ContestStage stage) {
        if (stage.getTargetType() != null
                && stage.getTargetType() == com.api.trekkey.domain.contest.entity.StageTargetType.PREVIOUS_PASSED) {
            return contestStageRepository
                    .findAllByContestIdOrderBySequenceNoAsc(stage.getContest().getId()).stream()
                    .filter(prev -> prev.getSequenceNo() < stage.getSequenceNo())
                    .filter(prev -> prev.getStatus() == StageStatus.COMPLETED)
                    .max(Comparator.comparing(ContestStage::getSequenceNo))
                    .map(prev -> entryRepository
                            .findAllByContestStageIdAndStatus(prev.getId(), EntryStatus.PASSED).stream()
                            .map(ContestStageEntry::getSubmission)
                            .toList())
                    .orElse(List.of());
        }
        //ALL_SUBMISSIONS (기본): 제출 완료된 전체 제출물
        return submissionRepository.findAllByContestId(stage.getContest().getId()).stream()
                .filter(submission -> submission.getStatus() == SubmissionStatus.SUBMITTED)
                .toList();
    }

    private Map<Long, List<BigDecimal>> collectScores(List<ContestStageEntry> entries) {
        if (entries.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<BigDecimal>> result = new HashMap<>();
        reviewRepository.findTotalScoresByEntryIds(entries.stream().map(ContestStageEntry::getId).toList())
                .forEach(row -> result
                        .computeIfAbsent((Long) row[0], key -> new ArrayList<>())
                        .add((BigDecimal) row[1]));
        return result;
    }

    private BigDecimal average(List<BigDecimal> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private List<EntryRes> getEntriesInternal(
            List<ContestStageEntry> entries, Map<Long, List<BigDecimal>> scoresByEntryId) {
        return entries.stream()
                .map(entry -> {
                    List<BigDecimal> scores = scoresByEntryId.getOrDefault(entry.getId(), List.of());
                    return EntryRes.of(entry, scores.size(), average(scores));
                })
                .toList();
    }
}
