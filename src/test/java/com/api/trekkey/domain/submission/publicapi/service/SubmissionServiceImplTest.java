package com.api.trekkey.domain.submission.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionSaveReq;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long ORGANIZATION_ID = 2L;
    private static final Long CONTEST_ID = 20L;
    private static final Long TEAM_ID = 30L;
    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 24, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private EntityManager entityManager;

    private SubmissionServiceImpl submissionService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        submissionService = new SubmissionServiceImpl(
                userRepository,
                contestRepository,
                contestStageRepository,
                teamRepository,
                submissionRepository,
                clock,
                entityManager
        );
    }

    @Test
    @DisplayName("대표 참가자는 제출 기간과 관계없이 자신의 제출물을 조회할 수 있다")
    void getSubmission_returnsOwnedSubmissionWithoutStageCheck() {
        Team team = givenReadContext(TeamStatus.APPROVED);
        Submission submission = submission(team, SubmissionStatus.SUBMITTED);
        given(submissionRepository.findByTeamId(TEAM_ID))
                .willReturn(Optional.of(submission));

        SubmissionRes response =
                submissionService.getSubmission(USER_ID, CONTEST_PUBLIC_ID);

        assertThat(response.publicId()).isEqualTo("submission-public-id");
        assertThat(response.title()).isEqualTo("AI 캠퍼스");
        assertThat(response.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        verifyNoInteractions(contestStageRepository);
    }

    @Test
    @DisplayName("승인 팀은 열린 제출 단계에서 제목을 정리해 초안을 생성한다")
    void saveDraft_createsTrimmedDraftForApprovedTeam() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.empty());
        given(submissionRepository.saveAndFlush(any(Submission.class)))
                .willAnswer(invocation -> {
                    Submission saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 40L);
                    ReflectionTestUtils.setField(
                            saved,
                            "publicId",
                            "submission-public-id"
                    );
                    return saved;
                });

        SubmissionRes response = submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("  AI 캠퍼스  ")
        );

        ArgumentCaptor<Submission> captor =
                ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).saveAndFlush(captor.capture());
        Submission saved = captor.getValue();
        assertThat(saved.getTeam()).isSameAs(team);
        assertThat(saved.getTitle()).isEqualTo("AI 캠퍼스");
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(response.publicId()).isEqualTo("submission-public-id");

        InOrder lockOrder = inOrder(
                contestStageRepository,
                entityManager,
                teamRepository,
                submissionRepository
        );
        lockOrder.verify(contestStageRepository)
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION
                );
        lockOrder.verify(entityManager)
                .refresh(team.getContest(), LockModeType.PESSIMISTIC_READ);
        lockOrder.verify(teamRepository)
                .findByContestIdAndLeaderUserIdForUpdate(CONTEST_ID, USER_ID);
        lockOrder.verify(submissionRepository)
                .findByTeamIdForUpdate(TEAM_ID);
    }

    @Test
    @DisplayName("기존 초안은 같은 행의 제목만 수정한다")
    void saveDraft_updatesExistingDraft() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.DRAFT);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        SubmissionRes response = submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("  수정 작품명  ")
        );

        assertThat(submission.getTitle()).isEqualTo("수정 작품명");
        assertThat(response.status()).isEqualTo(SubmissionStatus.DRAFT);
        verify(submissionRepository).flush();
        verify(submissionRepository, never()).saveAndFlush(any(Submission.class));
    }

    @Test
    @DisplayName("이미 제출한 작품은 초안 저장으로 수정할 수 없다")
    void saveDraft_rejectsSubmittedSubmission() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.SUBMITTED);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        assertThatThrownBy(() -> submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("수정 작품명")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);

        assertThat(submission.getTitle()).isEqualTo("AI 캠퍼스");
        verify(submissionRepository, never()).flush();
    }

    @Test
    @DisplayName("동시 생성으로 팀당 제출물 유니크 제약이 충돌하면 409로 변환한다")
    void saveDraft_convertsUniqueConstraintViolation() {
        givenMutationContext(TeamStatus.APPROVED, openStage());
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.empty());
        given(submissionRepository.saveAndFlush(any(Submission.class)))
                .willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("AI 캠퍼스")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("비활성 참가자의 기존 토큰으로는 제출물을 조회할 수 없다")
    void getSubmission_rejectsInactiveParticipant() {
        User participant = participant(UserStatus.INACTIVE, UserRole.PARTICIPANT);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(participant));

        assertThatThrownBy(() ->
                submissionService.getSubmission(USER_ID, CONTEST_PUBLIC_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_INVALID_TOKEN);

        verifyNoInteractions(contestRepository, teamRepository, submissionRepository);
    }

    @Test
    @DisplayName("현재 사용자 조직의 대회가 아니면 대회 없음으로 숨긴다")
    void getSubmission_hidesOtherOrganizationContest() {
        User participant = givenActiveParticipant();
        given(contestRepository.findByPublicIdAndOrganizationId(
                CONTEST_PUBLIC_ID,
                ORGANIZATION_ID
        )).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                submissionService.getSubmission(USER_ID, CONTEST_PUBLIC_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_NOT_FOUND);

        assertThat(participant.getOrganization().getId())
                .isEqualTo(ORGANIZATION_ID);
        verifyNoInteractions(teamRepository, submissionRepository);
    }

    @Test
    @DisplayName("다른 팀원이나 미참가 사용자는 대표자 제출물을 찾을 수 없다")
    void getSubmission_rejectsNonLeader() {
        givenContestContext();
        given(teamRepository.findByContestIdAndLeaderUserId(CONTEST_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                submissionService.getSubmission(USER_ID, CONTEST_PUBLIC_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        SubmissionErrorResponseCode.SUBMISSION_TEAM_NOT_FOUND);

        verifyNoInteractions(submissionRepository);
    }

    @Test
    @DisplayName("승인되지 않은 팀은 초안을 생성할 수 없다")
    void saveDraft_rejectsUnapprovedTeam() {
        givenMutationContext(TeamStatus.PENDING, openStage());

        assertThatThrownBy(() -> submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("AI 캠퍼스")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        SubmissionErrorResponseCode.SUBMISSION_TEAM_NOT_APPROVED);

        verifyNoInteractions(submissionRepository);
    }

    @Test
    @DisplayName("제출 단계가 없거나 둘 이상이면 대회 설정 오류로 처리한다")
    void saveDraft_rejectsAmbiguousSubmissionStage() {
        givenContestContext();
        ContestStage first = openStage();
        ContestStage second = openStage();
        given(contestStageRepository
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION
                )).willReturn(List.of(first, second));

        assertThatThrownBy(() -> submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("AI 캠퍼스")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_STAGE_INVALID);

        verifyNoInteractions(teamRepository, submissionRepository);
    }

    @Test
    @DisplayName("준비 중이거나 마감된 제출 단계에서는 초안을 저장할 수 없다")
    void saveDraft_rejectsClosedSubmissionStage() {
        givenContestContext();
        ContestStage closedStage = ContestStage.builder()
                .contest(contest())
                .name("제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(StageStatus.COMPLETED)
                .endsAt(LocalDateTime.of(2099, 1, 1, 0, 0))
                .build();
        given(contestStageRepository
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION
                )).willReturn(List.of(closedStage));

        assertThatThrownBy(() -> submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("AI 캠퍼스")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);

        verifyNoInteractions(teamRepository, submissionRepository);
    }

    @Test
    @DisplayName("단계 잠금 대기 중 대회가 종료되면 최신 상태를 다시 읽고 거부한다")
    void saveDraft_rejectsContestClosedBeforeStageLockAcquisition() {
        User participant = givenActiveParticipant();
        Contest contest = contest();
        given(contestRepository.findByPublicIdAndOrganizationId(
                CONTEST_PUBLIC_ID,
                ORGANIZATION_ID
        )).willReturn(Optional.of(contest));
        ContestStage stage = openStage();
        given(contestStageRepository
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION
                )).willReturn(List.of(stage));
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(
                    contest,
                    "status",
                    ContestStatus.AWARDED
            );
            return null;
        }).when(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_READ
        );

        assertThatThrownBy(() -> submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("AI 캠퍼스")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);

        assertThat(participant.getOrganization().getId())
                .isEqualTo(ORGANIZATION_ID);
        verifyNoInteractions(teamRepository, submissionRepository);
    }

    @Test
    @DisplayName("현재 시각이 제출 마감 시각과 같으면 제출을 거부한다")
    void saveDraft_rejectsExactDeadlineBoundary() {
        givenContestContext();
        ContestStage deadlineReached = ContestStage.builder()
                .contest(contest())
                .name("제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(StageStatus.OPEN)
                .startsAt(NOW.minusDays(1))
                .endsAt(NOW)
                .build();
        given(contestStageRepository
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION
                )).willReturn(List.of(deadlineReached));

        assertThatThrownBy(() -> submissionService.saveDraft(
                USER_ID,
                CONTEST_PUBLIC_ID,
                new SubmissionSaveReq("AI 캠퍼스")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);

        verifyNoInteractions(teamRepository, submissionRepository);
    }

    @Test
    @DisplayName("초안을 제출하면 제출 상태와 서버 제출 시각을 기록한다")
    void submit_changesDraftToSubmitted() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.DRAFT);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        SubmissionRes response =
                submissionService.submit(USER_ID, CONTEST_PUBLIC_ID);

        assertThat(response.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.submittedAt()).isNotNull();
        verify(submissionRepository).flush();
    }

    @Test
    @DisplayName("이미 제출된 작품의 제출 재시도는 제출 시각을 바꾸지 않는다")
    void submit_isIdempotentWhenAlreadySubmitted() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        LocalDateTime firstSubmittedAt =
                LocalDateTime.of(2026, 7, 24, 12, 0);
        Submission submission = Submission.builder()
                .publicId("submission-public-id")
                .team(team)
                .title("AI 캠퍼스")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(firstSubmittedAt)
                .build();
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        SubmissionRes response =
                submissionService.submit(USER_ID, CONTEST_PUBLIC_ID);

        assertThat(response.submittedAt()).isEqualTo(firstSubmittedAt);
        verify(submissionRepository, never()).flush();
    }

    @Test
    @DisplayName("철회된 작품은 다시 제출할 수 없다")
    void submit_rejectsWithdrawnSubmission() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.WITHDRAWN);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        assertThatThrownBy(() ->
                submissionService.submit(USER_ID, CONTEST_PUBLIC_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);

        verify(submissionRepository, never()).flush();
    }

    @Test
    @DisplayName("제출된 작품은 마감 전 초안으로 다시 열 수 있다")
    void reopen_changesSubmittedToDraft() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        LocalDateTime firstSubmittedAt =
                LocalDateTime.of(2026, 7, 24, 11, 0);
        Submission submission = Submission.builder()
                .publicId("submission-public-id")
                .team(team)
                .title("AI 캠퍼스")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(firstSubmittedAt)
                .build();
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        SubmissionRes response =
                submissionService.reopen(USER_ID, CONTEST_PUBLIC_ID);

        assertThat(response.status()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(response.submittedAt()).isEqualTo(firstSubmittedAt);
        verify(submissionRepository).flush();
    }

    @Test
    @DisplayName("철회된 작품은 초안으로 다시 열 수 없다")
    void reopen_rejectsWithdrawnSubmission() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.WITHDRAWN);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        assertThatThrownBy(() ->
                submissionService.reopen(USER_ID, CONTEST_PUBLIC_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);

        verify(submissionRepository, never()).flush();
    }

    @Test
    @DisplayName("제출된 작품을 철회하면 철회 상태로 변경한다")
    void withdraw_changesSubmittedToWithdrawn() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.SUBMITTED);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        SubmissionRes response =
                submissionService.withdraw(USER_ID, CONTEST_PUBLIC_ID);

        assertThat(response.status()).isEqualTo(SubmissionStatus.WITHDRAWN);
        verify(submissionRepository).flush();
    }

    @Test
    @DisplayName("아직 제출하지 않은 초안은 철회할 수 없다")
    void withdraw_rejectsDraftSubmission() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.DRAFT);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        assertThatThrownBy(() ->
                submissionService.withdraw(USER_ID, CONTEST_PUBLIC_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
        verify(submissionRepository, never()).flush();
    }

    @Test
    @DisplayName("이미 철회된 작품의 철회 재시도는 멱등 처리한다")
    void withdraw_isIdempotentWhenAlreadyWithdrawn() {
        Team team = givenMutationContext(TeamStatus.APPROVED, openStage());
        Submission submission = submission(team, SubmissionStatus.WITHDRAWN);
        given(submissionRepository.findByTeamIdForUpdate(TEAM_ID))
                .willReturn(Optional.of(submission));

        SubmissionRes response =
                submissionService.withdraw(USER_ID, CONTEST_PUBLIC_ID);

        assertThat(response.status()).isEqualTo(SubmissionStatus.WITHDRAWN);
        verify(submissionRepository, never()).flush();
    }

    private Team givenReadContext(TeamStatus teamStatus) {
        Contest contest = givenContestContext();
        Team team = team(contest, teamStatus);
        given(teamRepository.findByContestIdAndLeaderUserId(CONTEST_ID, USER_ID))
                .willReturn(Optional.of(team));
        return team;
    }

    private Team givenMutationContext(
            TeamStatus teamStatus,
            ContestStage submissionStage
    ) {
        Contest contest = givenContestContext();
        given(contestStageRepository
                .findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        CONTEST_ID,
                        StageType.SUBMISSION
                )).willReturn(List.of(submissionStage));
        Team team = team(contest, teamStatus);
        given(teamRepository.findByContestIdAndLeaderUserIdForUpdate(
                CONTEST_ID,
                USER_ID
        )).willReturn(Optional.of(team));
        return team;
    }

    private Contest givenContestContext() {
        User participant = givenActiveParticipant();
        Contest contest = contest();
        given(contestRepository.findByPublicIdAndOrganizationId(
                CONTEST_PUBLIC_ID,
                ORGANIZATION_ID
        )).willReturn(Optional.of(contest));
        assertThat(participant.getOrganization().getId())
                .isEqualTo(ORGANIZATION_ID);
        return contest;
    }

    private User givenActiveParticipant() {
        User participant =
                participant(UserStatus.ACTIVE, UserRole.PARTICIPANT);
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(participant));
        return participant;
    }

    private User participant(UserStatus status, UserRole role) {
        Organization organization =
                org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient()
                .when(organization.getId())
                .thenReturn(ORGANIZATION_ID);
        return User.builder()
                .id(USER_ID)
                .organization(organization)
                .role(role)
                .status(status)
                .build();
    }

    private Contest contest() {
        return Contest.builder()
                .id(CONTEST_ID)
                .publicId(CONTEST_PUBLIC_ID)
                .status(ContestStatus.REVIEWING)
                .build();
    }

    private Team team(Contest contest, TeamStatus status) {
        return Team.builder()
                .id(TEAM_ID)
                .contest(contest)
                .leaderUser(participant(UserStatus.ACTIVE, UserRole.PARTICIPANT))
                .status(status)
                .build();
    }

    private ContestStage openStage() {
        return ContestStage.builder()
                .contest(contest())
                .name("제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(StageStatus.OPEN)
                .startsAt(LocalDateTime.of(2020, 1, 1, 0, 0))
                .endsAt(LocalDateTime.of(2099, 1, 1, 0, 0))
                .build();
    }

    private Submission submission(Team team, SubmissionStatus status) {
        return Submission.builder()
                .id(40L)
                .publicId("submission-public-id")
                .team(team)
                .title("AI 캠퍼스")
                .status(status)
                .build();
    }
}
