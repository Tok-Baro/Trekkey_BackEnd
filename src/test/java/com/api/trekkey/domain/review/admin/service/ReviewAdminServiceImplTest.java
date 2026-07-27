package com.api.trekkey.domain.review.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.credential.integration.WorkCredentialIssuer;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.review.admin.web.dto.EntryDecisionReq;
import com.api.trekkey.domain.review.admin.web.dto.EntryRes;
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
import com.api.trekkey.domain.organization.entity.Organization;
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
class ReviewAdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ContestJudgeRepository contestJudgeRepository;

    @Mock
    private ContestStageEntryRepository entryRepository;

    @Mock
    private ReviewAssignmentRepository assignmentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private WorkCredentialIssuer workCredentialIssuer;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private ReviewAdminServiceImpl reviewAdminService;

    private Organization organization;
    private User admin;
    private Contest contest;
    private ContestStage stage;

    @BeforeEach
    void setUp() {
        reviewAdminService = new ReviewAdminServiceImpl(
                userRepository, contestRepository, contestStageRepository, submissionRepository,
                contestJudgeRepository, entryRepository, assignmentRepository, reviewRepository,
                workCredentialIssuer, Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC),
                new ReviewTokenSupport("http://localhost:5173"), adminAuditLogger);

        organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(1L);

        admin = mock(User.class);
        lenient().when(admin.getId()).thenReturn(100L);
        lenient().when(admin.getOrganization()).thenReturn(organization);
        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(admin));

        contest = mock(Contest.class);
        lenient().when(contest.getId()).thenReturn(200L);
        lenient().when(contest.getOrganization()).thenReturn(organization);

        stage = mock(ContestStage.class);
        lenient().when(stage.getId()).thenReturn(300L);
        lenient().when(stage.getContest()).thenReturn(contest);
        lenient().when(stage.getStageType()).thenReturn(StageType.REVIEW);
        lenient().when(stage.getName()).thenReturn("1차 심사");
        lenient().when(contestStageRepository.findById(300L)).thenReturn(Optional.of(stage));
    }

    @Test
    @DisplayName("라운드 시작 시 제출물이 잠기고 전 심사위원에게 배정된다")
    void openRound_createsEntriesAndAssignments() {
        given(stage.getStatus()).willReturn(StageStatus.PREPARING);
        given(contestJudgeRepository.findAllByContestId(200L))
                .willReturn(List.of(mock(ContestJudge.class), mock(ContestJudge.class)));
        Submission submission = submissionFixture();
        given(submissionRepository.findAllByContestId(200L)).willReturn(List.of(submission));
        AtomicLong entryIdSequence = new AtomicLong(400L);
        given(entryRepository.save(any(ContestStageEntry.class))).willAnswer(invocation -> {
            ContestStageEntry entry = invocation.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", entryIdSequence.getAndIncrement());
            return entry;
        });

        List<EntryRes> result = reviewAdminService.openRound(100L, 300L);

        assertThat(result).hasSize(1);
        assertThat(submission.isFinalized()).isTrue(); //첫 심사 시작 → 제출물 잠금
        verify(assignmentRepository, times(2)).save(any(ReviewAssignment.class)); //심사위원 2명 배정
        verify(stage).changeStatus(StageStatus.OPEN);
        verify(workCredentialIssuer).issueForFinalizedSubmission(submission); //첫 확정 시 작품 Credential 발급
    }

    @Test
    @DisplayName("심사위원이 없으면 라운드를 시작할 수 없다")
    void openRound_throwsWhenNoJudge() {
        given(stage.getStatus()).willReturn(StageStatus.PREPARING);
        given(contestJudgeRepository.findAllByContestId(200L)).willReturn(List.of());

        assertThatThrownBy(() -> reviewAdminService.openRound(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.ROUND_NO_JUDGE);
    }

    @Test
    @DisplayName("이미 시작된 라운드는 다시 시작할 수 없다")
    void openRound_throwsWhenAlreadyOpened() {
        given(stage.getStatus()).willReturn(StageStatus.OPEN);

        assertThatThrownBy(() -> reviewAdminService.openRound(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.ROUND_ALREADY_OPENED);
    }

    @Test
    @DisplayName("TOP_N 라운드 마감 시 평균순 순위와 통과/탈락이 확정된다")
    void finalizeRound_appliesTopNRule() {
        given(stage.getStatus()).willReturn(StageStatus.OPEN);
        given(stage.getPassRule()).willReturn(StagePassRule.TOP_N);
        given(stage.getPassCount()).willReturn(2);

        ContestStageEntry first = entryFixture(401L);
        ContestStageEntry second = entryFixture(402L);
        ContestStageEntry third = entryFixture(403L);
        given(entryRepository.findAllByContestStageIdOrderByIdAsc(300L))
                .willReturn(List.of(first, second, third));
        //제출된 심사 총점: entry401=90, entry402=[80,70](평균75), entry403=없음(0점)
        given(reviewRepository.findTotalScoresByEntryIds(any())).willReturn(List.of(
                new Object[]{401L, new BigDecimal("90")},
                new Object[]{402L, new BigDecimal("80")},
                new Object[]{402L, new BigDecimal("70")}));

        reviewAdminService.finalizeRound(100L, 300L);

        assertThat(first.getRankNo()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(EntryStatus.PASSED);
        assertThat(second.getRankNo()).isEqualTo(2);
        assertThat(second.getStatus()).isEqualTo(EntryStatus.PASSED);
        assertThat(second.getFinalScore()).isEqualByComparingTo("75");
        assertThat(third.getRankNo()).isEqualTo(3);
        assertThat(third.getStatus()).isEqualTo(EntryStatus.FAILED);
        assertThat(first.isFinalized()).isTrue();
        verify(stage).changeStatus(StageStatus.COMPLETED);
    }

    @Test
    @DisplayName("진행 중이 아닌 라운드는 마감할 수 없다")
    void finalizeRound_throwsWhenNotOpen() {
        given(stage.getStatus()).willReturn(StageStatus.PREPARING);

        assertThatThrownBy(() -> reviewAdminService.finalizeRound(100L, 300L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.ROUND_NOT_OPEN);
    }

    @Test
    @DisplayName("확정된 심사 대상은 수동 판정할 수 없다")
    void decideEntry_throwsWhenFinalized() {
        ContestStageEntry entry = entryFixture(401L);
        ReflectionTestUtils.setField(entry, "finalizedAt", LocalDateTime.now());
        given(entryRepository.findById(401L)).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> reviewAdminService.decideEntry(
                100L, 401L, new EntryDecisionReq(EntryStatus.PASSED, "동점 처리")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.ENTRY_ALREADY_FINALIZED);
    }

    //======= 헬퍼 메서드 ==========

    private Submission submissionFixture() {
        Team team = mock(Team.class);
        lenient().when(team.getName()).thenReturn("팀트레키");
        Submission submission = Submission.builder()
                .publicId("sub-pub-1")
                .team(team)
                .title("작품")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(submission, "id", 500L);
        return submission;
    }

    private ContestStageEntry entryFixture(Long id) {
        ContestStageEntry entry = ContestStageEntry.builder()
                .contestStage(stage)
                .submission(submissionFixture())
                .status(EntryStatus.IN_REVIEW)
                .build();
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }
}
