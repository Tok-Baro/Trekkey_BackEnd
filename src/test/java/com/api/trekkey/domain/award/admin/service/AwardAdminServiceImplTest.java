package com.api.trekkey.domain.award.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.credential.integration.AwardCredentialIssuer;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.DecisionType;
import com.api.trekkey.domain.review.entity.EntryStatus;
import com.api.trekkey.domain.review.repository.ContestStageEntryRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
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
    private ContestStageRepository contestStageRepository;

    @Mock
    private ContestStageEntryRepository entryRepository;

    @Mock
    private AwardRepository awardRepository;

    @Mock
    private AwardCredentialIssuer awardCredentialIssuer;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private AwardAdminServiceImpl awardAdminService;

    private Organization organization;
    private User admin;
    private Contest contest;
    private ContestStage stage;

    @BeforeEach
    void setUp() {
        awardAdminService = new AwardAdminServiceImpl(
                userRepository, contestRepository, contestStageRepository,
                entryRepository, awardRepository, awardCredentialIssuer, adminAuditLogger,
                Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC));

        organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(1L);

        admin = mock(User.class);
        lenient().when(admin.getId()).thenReturn(100L);
        lenient().when(admin.getOrganization()).thenReturn(organization);
        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(admin));

        contest = mock(Contest.class);
        lenient().when(contest.getId()).thenReturn(200L);
        lenient().when(contest.getOrganization()).thenReturn(organization);
        lenient().when(contest.getAwardCount()).thenReturn(2);
        lenient().when(contest.getPublicId()).thenReturn("contest-pub-1");
        lenient().when(contest.getTitle()).thenReturn("2026 AI 공모전");
        lenient().when(contestRepository.findByPublicId("contest-pub-1")).thenReturn(Optional.of(contest));

        stage = mock(ContestStage.class);
        lenient().when(stage.getId()).thenReturn(300L);
        lenient().when(stage.getContest()).thenReturn(contest);
        lenient().when(stage.getName()).thenReturn("최종 심사");
        lenient().when(contestStageRepository.findById(300L)).thenReturn(Optional.of(stage));

        lenient().when(awardRepository.save(any(Award.class))).thenAnswer(invocation -> {
            Award award = invocation.getArgument(0);
            ReflectionTestUtils.setField(award, "publicId", "award-pub");
            return award;
        });
    }

    @Test
    @DisplayName("확정 라운드의 통과작을 순위순으로 awardCount만큼 후보 산출한다")
    void calculateAwards_createsCandidatesByRank() {
        given(stage.getStatus()).willReturn(StageStatus.COMPLETED);
        given(awardRepository.existsByTeamContestIdAndStatus(200L, AwardStatus.CONFIRMED)).willReturn(false);
        given(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(200L)).willReturn(List.of());
        //통과작 3개지만 awardCount=2 → 상위 2개만 (rankNo 역순으로 넣어 정렬 검증)
        //fixture는 스터빙 밖에서 먼저 생성한다 (mock 중첩 스터빙 방지)
        ContestStageEntry third = entryFixture(3, "팀C");
        ContestStageEntry first = entryFixture(1, "팀A");
        ContestStageEntry second = entryFixture(2, "팀B");
        given(entryRepository.findAllByContestStageIdAndStatus(300L, EntryStatus.PASSED))
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
    @DisplayName("확정되지 않은 라운드로는 수상을 산출할 수 없다")
    void calculateAwards_throwsWhenRoundNotFinalized() {
        given(stage.getStatus()).willReturn(StageStatus.OPEN);

        assertThatThrownBy(() -> awardAdminService.calculateAwards(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(AwardErrorResponseCode.AWARD_ROUND_NOT_FINALIZED);
    }

    @Test
    @DisplayName("확정된 수상이 있으면 재산출할 수 없다")
    void calculateAwards_throwsWhenAlreadyConfirmed() {
        given(stage.getStatus()).willReturn(StageStatus.COMPLETED);
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
        given(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(200L)).willReturn(List.of(candidate));

        List<AwardRes> result = awardAdminService.confirmAwards(100L, "contest-pub-1");

        assertThat(result.get(0).status()).isEqualTo(AwardStatus.CONFIRMED);
        assertThat(result.get(0).confirmedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 26, 12, 0));
        verify(contest).changeStatus(ContestStatus.AWARDED);
        verify(awardCredentialIssuer).issueForConfirmedAward(candidate); //확정과 같은 트랜잭션에서 Credential 발급
    }

    @Test
    @DisplayName("후보가 없으면 확정할 수 없다")
    void confirmAwards_throwsWhenNoCandidate() {
        given(awardRepository.findAllByTeamContestIdOrderByAwardRankNoAsc(200L)).willReturn(List.of());

        assertThatThrownBy(() -> awardAdminService.confirmAwards(100L, "contest-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(AwardErrorResponseCode.AWARD_NO_CANDIDATE);
    }

    //======= 헬퍼 메서드 ==========

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(400L);

    private ContestStageEntry entryFixture(int rankNo, String teamName) {
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
        ContestStageEntry entry = ContestStageEntry.builder()
                .contestStage(stage)
                .submission(submission)
                .status(EntryStatus.PASSED)
                .build();
        ReflectionTestUtils.setField(entry, "id", ID_SEQUENCE.getAndIncrement());
        entry.finalizeByRule(new BigDecimal(100 - rankNo), rankNo, EntryStatus.PASSED, LocalDateTime.now());
        assertThat(entry.getDecisionType()).isEqualTo(DecisionType.RULE);
        return entry;
    }

    private Award awardFixture(AwardStatus status) {
        ContestStageEntry entry = entryFixture(1, "팀A");
        return Award.builder()
                .publicId("award-pub-1")
                .contestStageEntry(entry)
                .team(entry.getSubmission().getTeam())
                .awardRankNo(1)
                .prize("대상")
                .status(status)
                .certificateNo("2026-C200-001")
                .build();
    }
}
