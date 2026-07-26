package com.api.trekkey.domain.award.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.exception.AwardErrorResponseCode;
import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.EntryStatus;
import com.api.trekkey.domain.review.repository.ContestStageEntryRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AwardAdminServiceImpl implements AwardAdminService {

    private static final List<String> PRIZE_LABELS = List.of("대상", "최우수상", "우수상", "장려상", "입선");
    private static final String TARGET_TYPE_CONTEST = "CONTEST";
    private static final String TARGET_TYPE_STAGE = "STAGE";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final ContestStageEntryRepository entryRepository;
    private final AwardRepository awardRepository;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    @Transactional
    public List<AwardRes> calculateAwards(Long adminUserId, Long stageId) {
        User admin = findAdmin(adminUserId);

        ContestStage stage = contestStageRepository.findById(stageId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.STAGE_NOT_FOUND));
        Contest contest = stage.getContest();
        validateSameOrganization(contest, admin);

        //확정(마감)된 라운드만 수상 산출 근거가 된다
        if (stage.getStatus() != StageStatus.COMPLETED) {
            throw new CustomException(AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
        }
        //이미 확정된 수상이 있으면 재산출 불가
        if (awardRepository.existsByTeamContestIdAndStatus(contest.getId(), AwardStatus.CONFIRMED)) {
            throw new CustomException(AwardErrorResponseCode.AWARD_ALREADY_CONFIRMED);
        }

        //통과작을 확정 순위순으로 정렬해 awardCount만큼 후보 생성
        List<ContestStageEntry> passedEntries = entryRepository
                .findAllByContestStageIdAndStatus(stage.getId(), EntryStatus.PASSED).stream()
                .filter(ContestStageEntry::isFinalized)
                .sorted(Comparator.comparing(ContestStageEntry::getRankNo))
                .toList();
        if (passedEntries.isEmpty()) {
            throw new CustomException(AwardErrorResponseCode.AWARD_NO_PASSED_ENTRY);
        }

        //재산출: 기존 후보(CANDIDATE/HELD)는 교체한다
        awardRepository.deleteAll(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(contest.getId()));
        awardRepository.flush();

        int awardLimit = Math.min(contest.getAwardCount(), passedEntries.size());
        int year = LocalDateTime.now().getYear();
        List<Award> awards = new ArrayList<>();
        for (int i = 0; i < awardLimit; i++) {
            ContestStageEntry entry = passedEntries.get(i);
            int rank = i + 1;
            awards.add(awardRepository.save(Award.builder()
                    .contestStageEntry(entry)
                    .team(entry.getSubmission().getTeam())
                    .awardRankNo(rank)
                    .prize(rank <= PRIZE_LABELS.size() ? PRIZE_LABELS.get(rank - 1) : rank + "위")
                    .status(AwardStatus.CANDIDATE)
                    .certificateNo(String.format("%d-C%d-%03d", year, contest.getId(), rank))
                    .build()));
        }

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.AWARD_CALCULATE,
                TARGET_TYPE_STAGE, stageId, stage.getName() + " 후보 " + awards.size() + "건 산출");

        return awards.stream().map(AwardRes::from).toList();
    }

    @Override
    public List<AwardRes> getAwards(Long adminUserId, String contestPublicId) {
        User admin = findAdmin(adminUserId);
        Contest contest = findContestInOrganization(contestPublicId, admin);

        return awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(contest.getId()).stream()
                .map(AwardRes::from)
                .toList();
    }

    @Override
    @Transactional
    public List<AwardRes> confirmAwards(Long adminUserId, String contestPublicId) {
        User admin = findAdmin(adminUserId);
        Contest contest = findContestInOrganization(contestPublicId, admin);

        List<Award> awards = awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(contest.getId());
        List<Award> candidates = awards.stream()
                .filter(award -> award.getStatus() == AwardStatus.CANDIDATE)
                .toList();
        if (candidates.isEmpty()) {
            throw new CustomException(AwardErrorResponseCode.AWARD_NO_CANDIDATE);
        }

        LocalDateTime now = LocalDateTime.now();
        candidates.forEach(award -> award.confirm(now));
        //수상 확정 → 대회 종결 상태 (Credential 발급 원천 완성, erd-mvp §6)
        contest.changeStatus(ContestStatus.AWARDED);

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.AWARD_CONFIRM,
                TARGET_TYPE_CONTEST, contest.getId(), contest.getTitle() + " " + candidates.size() + "건 확정");

        return awards.stream().map(AwardRes::from).toList();
    }

    //======= 헬퍼 메서드 ==========

    private User findAdmin(Long adminUserId) {
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }

    private Contest findContestInOrganization(String contestPublicId, User admin) {
        Contest contest = contestRepository.findByPublicId(contestPublicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        validateSameOrganization(contest, admin);
        return contest;
    }

    private void validateSameOrganization(Contest contest, User admin) {
        if (!contest.getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }
}
