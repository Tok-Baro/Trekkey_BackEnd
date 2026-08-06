package com.api.trekkey.domain.award.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.exception.AwardErrorResponseCode;
import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.credential.integration.AwardCredentialIssuer;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AwardAdminServiceImpl implements AwardAdminService {

    private static final List<String> PRIZE_LABELS = List.of("대상", "최우수상", "우수상", "장려상", "입선");
    private static final String TARGET_TYPE_CONTEST = "CONTEST";
    private static final String TARGET_TYPE_REVIEW_ROUND = "REVIEW_ROUND";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewRoundEntryRepository entryRepository;
    private final AwardRepository awardRepository;
    private final AwardCredentialIssuer awardCredentialIssuer;
    private final AdminAuditLogger adminAuditLogger;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public List<AwardRes> calculateAwards(Long adminUserId, Long roundId) {
        User admin = findAdmin(adminUserId);

        ReviewRound requestedRound = reviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND));
        Contest contest = requestedRound.getContest();
        validateSameOrganization(contest, admin);

        // 대회를 먼저 잠가 라운드 추가와 수상 산출이 서로 엇갈리지 않게 한다.
        List<ReviewRound> rounds = lockContestAndRounds(contest);
        ReviewRound round = rounds.stream()
                .filter(candidate -> candidate.getId().equals(roundId))
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND));
        ReviewRound finalRound = findFinalRound(rounds);

        // 최종 ERD 규칙상 가장 마지막 라운드만 수상의 공식 원천이 된다.
        if (!finalRound.getId().equals(round.getId())) {
            throw new CustomException(
                    AwardErrorResponseCode.AWARD_FINAL_ROUND_REQUIRED);
        }
        validateAllRoundsFinalized(rounds);
        // 확정(마감)된 라운드만 수상 산출 근거가 된다.
        if (round.getStatus() != ReviewRoundStatus.FINALIZED) {
            throw new CustomException(AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
        }
        //이미 확정된 수상이 있으면 재산출 불가
        if (awardRepository.existsByTeamContestIdAndStatus(contest.getId(), AwardStatus.CONFIRMED)) {
            throw new CustomException(AwardErrorResponseCode.AWARD_ALREADY_CONFIRMED);
        }

        //통과작을 확정 순위순으로 정렬해 awardCount만큼 후보 생성
        List<ReviewRoundEntry> selectedEntries = entryRepository
                .findAllByReviewRoundIdAndStatus(
                        round.getId(),
                        ReviewRoundEntryStatus.SELECTED).stream()
                .filter(ReviewRoundEntry::isFinalized)
                .sorted(Comparator
                        .comparing(
                                ReviewRoundEntry::getRankNo,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ReviewRoundEntry::getId))
                .toList();
        if (selectedEntries.isEmpty()) {
            throw new CustomException(AwardErrorResponseCode.AWARD_NO_PASSED_ENTRY);
        }

        //재산출: 기존 후보(CANDIDATE/HELD)는 교체한다
        awardRepository.deleteAll(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(contest.getId()));
        awardRepository.flush();

        int awardLimit = Math.min(contest.getAwardCount(), selectedEntries.size());
        int year = nowUtc().getYear();
        List<Award> awards = new ArrayList<>();
        for (int i = 0; i < awardLimit; i++) {
            ReviewRoundEntry entry = selectedEntries.get(i);
            int rank = i + 1;
            awards.add(awardRepository.save(Award.builder()
                    .reviewRoundEntry(entry)
                    .team(entry.getSubmission().getTeam())
                    .awardRankNo(rank)
                    .prize(rank <= PRIZE_LABELS.size() ? PRIZE_LABELS.get(rank - 1) : rank + "위")
                    .status(AwardStatus.CANDIDATE)
                    .certificateNo(String.format("%d-C%d-%03d", year, contest.getId(), rank))
                    .build()));
        }

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.AWARD_CALCULATE,
                TARGET_TYPE_REVIEW_ROUND, roundId,
                round.getName() + " 후보 " + awards.size() + "건 산출");

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
        List<ReviewRound> rounds = lockContestAndRounds(contest);
        ReviewRound finalRound = findFinalRound(rounds);
        if (finalRound.getStatus() != ReviewRoundStatus.FINALIZED) {
            throw new CustomException(
                    AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
        }

        List<Award> awards = awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(contest.getId());
        List<Award> candidates = awards.stream()
                .filter(award -> award.getStatus() == AwardStatus.CANDIDATE)
                .toList();
        if (candidates.isEmpty()) {
            throw new CustomException(AwardErrorResponseCode.AWARD_NO_CANDIDATE);
        }
        if (candidates.stream().anyMatch(award ->
                !award.getReviewRoundEntry()
                        .getReviewRound()
                        .getId()
                        .equals(finalRound.getId()))) {
            throw new CustomException(
                    AwardErrorResponseCode.AWARD_FINAL_ROUND_REQUIRED);
        }
        validateAllRoundsFinalized(rounds);
        validateCandidatesCurrent(
                contest,
                finalRound,
                candidates);

        LocalDateTime now = nowUtc();
        candidates.forEach(award -> award.confirm(now));
        //수상 확정 → 대회 종결 상태 (Credential 발급 원천 완성, erd-mvp §6)
        contest.changeStatus(ContestStatus.AWARDED);
        //확정과 수상 Credential 발급을 한 트랜잭션으로 — 발급 실패 시 확정도 롤백 (erd-mvp §6 원자성)
        candidates.forEach(awardCredentialIssuer::issueForConfirmedAward);

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.AWARD_CONFIRM,
                TARGET_TYPE_CONTEST, contest.getId(), contest.getTitle() + " " + candidates.size() + "건 확정");

        return awards.stream().map(AwardRes::from).toList();
    }

    //======= 헬퍼 메서드 ==========

    private User findAdmin(Long adminUserId) {
        User user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(
                    UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
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

    private List<ReviewRound> lockContestAndRounds(Contest contest) {
        entityManager.refresh(contest, LockModeType.PESSIMISTIC_WRITE);
        List<ReviewRound> rounds = reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(
                        contest.getId());
        if (rounds.isEmpty()) {
            throw new CustomException(
                    AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
        }
        return rounds;
    }

    private ReviewRound findFinalRound(List<ReviewRound> rounds) {
        return rounds.stream()
                .max(Comparator.comparingInt(ReviewRound::getRoundNo))
                .orElseThrow(() -> new CustomException(
                        AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED));
    }

    private void validateAllRoundsFinalized(List<ReviewRound> rounds) {
        if (rounds.stream().anyMatch(round ->
                round.getStatus() != ReviewRoundStatus.FINALIZED)) {
            throw new CustomException(
                    AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
        }
    }

    private void validateCandidatesCurrent(
            Contest contest,
            ReviewRound finalRound,
            List<Award> candidates
    ) {
        List<ReviewRoundEntry> selectedEntries = entryRepository
                .findAllByReviewRoundIdAndStatus(
                        finalRound.getId(),
                        ReviewRoundEntryStatus.SELECTED)
                .stream()
                .filter(ReviewRoundEntry::isFinalized)
                .sorted(Comparator
                        .comparing(
                                ReviewRoundEntry::getRankNo,
                                Comparator.nullsLast(
                                        Integer::compareTo))
                        .thenComparing(ReviewRoundEntry::getId))
                .toList();
        int expectedCount = Math.min(
                contest.getAwardCount(),
                selectedEntries.size());
        List<Award> orderedCandidates = candidates.stream()
                .sorted(Comparator
                        .comparingInt(Award::getAwardRankNo)
                        .thenComparing(Award::getId,
                                Comparator.nullsLast(Long::compareTo)))
                .toList();

        if (orderedCandidates.size() != expectedCount) {
            throw new CustomException(
                    AwardErrorResponseCode.AWARD_CANDIDATES_STALE);
        }
        for (int index = 0; index < expectedCount; index++) {
            Award candidate = orderedCandidates.get(index);
            ReviewRoundEntry expectedEntry =
                    selectedEntries.get(index);
            if (candidate.getAwardRankNo() != index + 1
                    || !Objects.equals(
                    candidate.getReviewRoundEntry().getId(),
                    expectedEntry.getId())) {
                throw new CustomException(
                        AwardErrorResponseCode
                                .AWARD_CANDIDATES_STALE);
            }
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
