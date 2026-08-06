package com.api.trekkey.domain.award.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.exception.AwardErrorResponseCode;
import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.credential.integration.AwardCredentialIssuer;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ReviewDecisionType;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AwardAdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ReviewRoundRepository reviewRoundRepository;

    @Mock
    private ReviewRoundEntryRepository entryRepository;

    @Mock
    private AwardRepository awardRepository;

    @Mock
    private AwardCredentialIssuer awardCredentialIssuer;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    @Mock
    private EntityManager entityManager;

    private AwardAdminServiceImpl awardAdminService;

    private Organization organization;
    private User admin;
    private Contest contest;
    private ReviewRound round;

    @BeforeEach
    void setUp() {
        awardAdminService = new AwardAdminServiceImpl(
                userRepository, contestRepository, reviewRoundRepository,
                entryRepository, awardRepository, awardCredentialIssuer, adminAuditLogger,
                entityManager,
                Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC));

        organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(1L);

        admin = mock(User.class);
        lenient().when(admin.getId()).thenReturn(100L);
        lenient().when(admin.getOrganization()).thenReturn(organization);
        lenient().when(admin.getRole()).thenReturn(UserRole.ADMIN);
        lenient().when(admin.getStatus()).thenReturn(UserStatus.ACTIVE);
        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(admin));

        contest = mock(Contest.class);
        lenient().when(contest.getId()).thenReturn(200L);
        lenient().when(contest.getOrganization()).thenReturn(organization);
        lenient().when(contest.getAwardCount()).thenReturn(2);
        lenient().when(contest.getPublicId()).thenReturn("contest-pub-1");
        lenient().when(contest.getTitle()).thenReturn("2026 AI 공모전");
        lenient().when(contestRepository.findByPublicId("contest-pub-1")).thenReturn(Optional.of(contest));

        round = mock(ReviewRound.class);
        lenient().when(round.getId()).thenReturn(300L);
        lenient().when(round.getContest()).thenReturn(contest);
        lenient().when(round.getName()).thenReturn("최종 심사");
        lenient().when(reviewRoundRepository.findById(300L)).thenReturn(Optional.of(round));
        lenient().when(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(200L))
                .thenReturn(List.of(round));

        lenient().when(awardRepository.save(any(Award.class))).thenAnswer(invocation -> {
            Award award = invocation.getArgument(0);
            ReflectionTestUtils.setField(award, "publicId", "award-pub");
            return award;
        });
    }

    @Test
    @DisplayName("확정 라운드의 통과작을 순위순으로 awardCount만큼 후보 산출한다")
    void calculateAwards_createsCandidatesByRank() {
        given(round.getStatus()).willReturn(ReviewRoundStatus.FINALIZED);
        given(awardRepository.existsByTeamContestIdAndStatus(200L, AwardStatus.CONFIRMED)).willReturn(false);
        given(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(200L)).willReturn(List.of());
        //통과작 3개지만 awardCount=2 → 상위 2개만 (rankNo 역순으로 넣어 정렬 검증)
        //fixture는 스터빙 밖에서 먼저 생성한다 (mock 중첩 스터빙 방지)
        ReviewRoundEntry third = entryFixture(3, "팀C");
        ReviewRoundEntry first = entryFixture(1, "팀A");
        ReviewRoundEntry second = entryFixture(2, "팀B");
        given(entryRepository.findAllByReviewRoundIdAndStatus(
                300L, ReviewRoundEntryStatus.SELECTED))
                .willReturn(List.of(third, first, second));

        List<AwardRes> result = awardAdminService.calculateAwards(100L, 300L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).prize()).isEqualTo("대상");
        assertThat(result.get(0).teamName()).isEqualTo("팀A");
        assertThat(result.get(0).contestPublicId()).isEqualTo("contest-pub-1");
        assertThat(result.get(0).teamPublicId()).isEqualTo("team-팀A");
        assertThat(result.get(1).prize()).isEqualTo("최우수상");
        assertThat(result.get(1).teamName()).isEqualTo("팀B");
        assertThat(result.get(0).certificateNo()).matches("\\d{4}-C200-001");
        assertThat(result.get(0).status()).isEqualTo(AwardStatus.CANDIDATE);
    }

    @Test
    @DisplayName("DB에서 비활성화된 관리자는 남은 JWT로 수상 작업을 수행할 수 없다")
    void getAwards_rejectsInactiveAdmin() {
        given(admin.getStatus()).willReturn(UserStatus.INACTIVE);

        assertThatThrownBy(() ->
                awardAdminService.getAwards(
                        100L,
                        "contest-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e ->
                        ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        UserErrorResponseCode.USER_INVALID_TOKEN);
    }

    @Test
    @DisplayName("심사 없는 수동 선정 결과도 명시된 순위대로 수상 후보를 산출한다")
    void calculateAwards_ordersManualEntriesWithoutScores() {
        given(round.getStatus()).willReturn(ReviewRoundStatus.FINALIZED);
        given(awardRepository.existsByTeamContestIdAndStatus(
                200L,
                AwardStatus.CONFIRMED)).willReturn(false);
        given(awardRepository
                .findAllByTeamContestIdOrderByAwardRankNoAsc(200L))
                .willReturn(List.of());
        ReviewRoundEntry second =
                manualEntryFixture(2, "팀B");
        ReviewRoundEntry first =
                manualEntryFixture(1, "팀A");
        given(entryRepository.findAllByReviewRoundIdAndStatus(
                300L,
                ReviewRoundEntryStatus.SELECTED))
                .willReturn(List.of(second, first));

        List<AwardRes> result =
                awardAdminService.calculateAwards(100L, 300L);

        assertThat(result)
                .extracting(AwardRes::teamName)
                .containsExactly("팀A", "팀B");
    }

    @Test
    @DisplayName("확정되지 않은 라운드로는 수상을 산출할 수 없다")
    void calculateAwards_throwsWhenRoundNotFinalized() {
        given(round.getStatus()).willReturn(ReviewRoundStatus.OPEN);

        assertThatThrownBy(() -> awardAdminService.calculateAwards(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
    }

    @Test
    @DisplayName("앞선 라운드가 남아 있으면 마지막 라운드가 확정돼도 수상을 산출할 수 없다")
    void calculateAwards_rejectsWhenAnyPreviousRoundIsOpen() {
        ReviewRound previousRound = mock(ReviewRound.class);
        given(previousRound.getId()).willReturn(299L);
        given(previousRound.getRoundNo()).willReturn(1);
        given(previousRound.getStatus()).willReturn(ReviewRoundStatus.OPEN);
        given(round.getRoundNo()).willReturn(2);
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(200L))
                .willReturn(List.of(previousRound, round));

        assertThatThrownBy(() ->
                awardAdminService.calculateAwards(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e ->
                        ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
    }

    @Test
    @DisplayName("마지막 라운드가 아니면 확정됐더라도 수상을 산출할 수 없다")
    void calculateAwards_throwsWhenRoundIsNotFinalRound() {
        ReviewRound laterRound = mock(ReviewRound.class);
        given(round.getRoundNo()).willReturn(1);
        given(laterRound.getId()).willReturn(301L);
        given(laterRound.getRoundNo()).willReturn(2);
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(200L))
                .willReturn(List.of(round, laterRound));

        assertThatThrownBy(() -> awardAdminService.calculateAwards(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(AwardErrorResponseCode.AWARD_FINAL_ROUND_REQUIRED);
    }

    @Test
    @DisplayName("확정된 수상이 있으면 재산출할 수 없다")
    void calculateAwards_throwsWhenAlreadyConfirmed() {
        given(round.getStatus()).willReturn(ReviewRoundStatus.FINALIZED);
        given(awardRepository.existsByTeamContestIdAndStatus(200L, AwardStatus.CONFIRMED)).willReturn(true);

        assertThatThrownBy(() -> awardAdminService.calculateAwards(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(AwardErrorResponseCode.AWARD_ALREADY_CONFIRMED);
    }

    @Test
    @DisplayName("수상 확정 시 후보가 CONFIRMED되고 대회가 AWARDED로 전환된다")
    void confirmAwards_confirmsAndClosesContest() {
        Award candidate = awardFixture(AwardStatus.CANDIDATE);
        given(round.getStatus()).willReturn(ReviewRoundStatus.FINALIZED);
        given(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(200L)).willReturn(List.of(candidate));
        given(entryRepository.findAllByReviewRoundIdAndStatus(
                300L,
                ReviewRoundEntryStatus.SELECTED
        )).willReturn(List.of(candidate.getReviewRoundEntry()));

        List<AwardRes> result = awardAdminService.confirmAwards(100L, "contest-pub-1");

        assertThat(result.get(0).status()).isEqualTo(AwardStatus.CONFIRMED);
        assertThat(result.get(0).confirmedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 26, 12, 0));
        verify(contest).changeStatus(ContestStatus.AWARDED);
        verify(awardCredentialIssuer).issueForConfirmedAward(candidate); //확정과 같은 트랜잭션에서 Credential 발급
    }

    @Test
    @DisplayName("후보 산출 뒤 수상 인원이 바뀌면 재산출 전에는 확정할 수 없다")
    void confirmAwards_rejectsCandidatesStaleAfterAwardCountChange() {
        ReviewRoundEntry first = entryFixture(1, "팀A");
        ReviewRoundEntry second = entryFixture(2, "팀B");
        Award firstCandidate = awardForEntry(first, 1);
        Award secondCandidate = awardForEntry(second, 2);
        given(round.getStatus()).willReturn(ReviewRoundStatus.FINALIZED);
        given(contest.getAwardCount()).willReturn(1);
        given(awardRepository
                .findAllByTeamContestIdOrderByAwardRankNoAsc(200L))
                .willReturn(List.of(firstCandidate, secondCandidate));
        given(entryRepository.findAllByReviewRoundIdAndStatus(
                300L,
                ReviewRoundEntryStatus.SELECTED
        )).willReturn(List.of(first, second));

        assertThatThrownBy(() -> awardAdminService.confirmAwards(
                100L,
                "contest-pub-1"
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e ->
                        ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        AwardErrorResponseCode.AWARD_CANDIDATES_STALE);

        assertThat(firstCandidate.getStatus())
                .isEqualTo(AwardStatus.CANDIDATE);
        assertThat(secondCandidate.getStatus())
                .isEqualTo(AwardStatus.CANDIDATE);
        verifyNoInteractions(awardCredentialIssuer);
    }

    @Test
    @DisplayName("후보가 없으면 확정할 수 없다")
    void confirmAwards_throwsWhenNoCandidate() {
        given(round.getStatus()).willReturn(ReviewRoundStatus.FINALIZED);
        given(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(200L)).willReturn(List.of());

        assertThatThrownBy(() -> awardAdminService.confirmAwards(100L, "contest-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(AwardErrorResponseCode.AWARD_NO_CANDIDATE);
    }

    @Test
    @DisplayName("후보 산출 뒤 더 높은 라운드가 추가되면 기존 후보를 확정할 수 없다")
    void confirmAwards_throwsWhenCandidateIsNotFromFinalRound() {
        ReviewRound laterRound = mock(ReviewRound.class);
        given(round.getRoundNo()).willReturn(1);
        given(laterRound.getId()).willReturn(301L);
        given(laterRound.getRoundNo()).willReturn(2);
        given(laterRound.getStatus())
                .willReturn(ReviewRoundStatus.FINALIZED);
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(200L))
                .willReturn(List.of(round, laterRound));
        Award existingCandidate =
                awardFixture(AwardStatus.CANDIDATE);
        given(awardRepository
                .findAllByTeamContestIdOrderByAwardRankNoAsc(200L))
                .willReturn(List.of(existingCandidate));

        assertThatThrownBy(() ->
                awardAdminService.confirmAwards(
                        100L,
                        "contest-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(
                        AwardErrorResponseCode
                                .AWARD_FINAL_ROUND_REQUIRED);
    }

    //======= 헬퍼 메서드 ==========

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(400L);

    private ReviewRoundEntry entryFixture(int rankNo, String teamName) {
        Team team = mock(Team.class);
        lenient().when(team.getPublicId()).thenReturn("team-" + teamName);
        lenient().when(team.getName()).thenReturn(teamName);
        lenient().when(team.getContest()).thenReturn(contest);
        Submission submission = Submission.builder()
                .publicId("sub-" + teamName)
                .team(team)
                .title(teamName + " 작품")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build();
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(round)
                .submission(submission)
                .status(ReviewRoundEntryStatus.SELECTED)
                .finalScore(new BigDecimal(100 - rankNo))
                .rankNo(rankNo)
                .decisionType(ReviewDecisionType.RULE)
                .finalizedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(entry, "id", ID_SEQUENCE.getAndIncrement());
        assertThat(entry.getDecisionType()).isEqualTo(ReviewDecisionType.RULE);
        return entry;
    }

    private ReviewRoundEntry manualEntryFixture(
            int rankNo,
            String teamName
    ) {
        Team team = mock(Team.class);
        lenient().when(team.getPublicId())
                .thenReturn("team-" + teamName);
        lenient().when(team.getName()).thenReturn(teamName);
        lenient().when(team.getContest()).thenReturn(contest);
        Submission submission = Submission.builder()
                .publicId("sub-" + teamName)
                .team(team)
                .title(teamName + " 작품")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build();
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(round)
                .submission(submission)
                .status(ReviewRoundEntryStatus.SELECTED)
                .rankNo(rankNo)
                .decisionType(ReviewDecisionType.MANUAL)
                .decidedByUser(admin)
                .decisionReason("위원회 선정")
                .finalizedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(
                entry,
                "id",
                ID_SEQUENCE.getAndIncrement());
        assertThat(entry.isFinalized()).isTrue();
        return entry;
    }

    private Award awardFixture(AwardStatus status) {
        ReviewRoundEntry entry = entryFixture(1, "팀A");
        return Award.builder()
                .publicId("award-pub-1")
                .reviewRoundEntry(entry)
                .team(entry.getSubmission().getTeam())
                .awardRankNo(1)
                .prize("대상")
                .status(status)
                .certificateNo("2026-C200-001")
                .build();
    }

    private Award awardForEntry(
            ReviewRoundEntry entry,
            int awardRankNo
    ) {
        return Award.builder()
                .publicId("award-pub-" + awardRankNo)
                .reviewRoundEntry(entry)
                .team(entry.getSubmission().getTeam())
                .awardRankNo(awardRankNo)
                .prize(awardRankNo + "위")
                .status(AwardStatus.CANDIDATE)
                .certificateNo(
                        "2026-C200-" + String.format(
                                "%03d",
                                awardRankNo))
                .build();
    }
}
