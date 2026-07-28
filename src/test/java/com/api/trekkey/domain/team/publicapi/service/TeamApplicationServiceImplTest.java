package com.api.trekkey.domain.team.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.EntryStatus;
import com.api.trekkey.domain.review.repository.ContestStageEntryRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes.Step;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes.StepStatus;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes.StepType;
import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantSearchRes;
import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantTeamRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationRes;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamApplicationServiceImplTest {

    private static final Set<ContestStatus> PUBLIC_STATUSES = Set.of(
            ContestStatus.APPLICATION_OPEN,
            ContestStatus.REVIEWING,
            ContestStatus.AWARDED);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private ContestStageEntryRepository contestStageEntryRepository;

    @Mock
    private AwardRepository awardRepository;

    @InjectMocks
    private TeamApplicationServiceImpl teamApplicationService;

    @Test
    @DisplayName("본인의 참가 신청을 최신순 응답으로 매핑한다")
    void getMyApplications_returnsMappedApplications() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(org.mockito.Mockito.mock(User.class)));
        LocalDateTime newerCreatedAt = LocalDateTime.of(2026, 7, 24, 15, 30);
        LocalDateTime newerUpdatedAt = LocalDateTime.of(2026, 7, 24, 16, 10);
        LocalDateTime olderCreatedAt = LocalDateTime.of(2026, 7, 20, 9, 0);
        LocalDateTime olderUpdatedAt = LocalDateTime.of(2026, 7, 21, 11, 20);
        Team newerTeam = application(
                "newer-contest",
                "AI 창의 경진대회",
                "SW중심대학사업단",
                ParticipationType.TEAM,
                "트랙키 팀",
                TeamStatus.PENDING,
                newerCreatedAt,
                newerUpdatedAt);
        Team olderTeam = application(
                "older-contest",
                "캠퍼스 아이디어톤",
                "학생지원처",
                ParticipationType.INDIVIDUAL,
                "김참가",
                TeamStatus.APPROVED,
                olderCreatedAt,
                olderUpdatedAt);
        User participant = org.mockito.Mockito.mock(User.class);
        given(teamMemberRepository.findAllWithTeamAndContestByUserId(10L))
                .willReturn(List.of(
                        teamMember(newerTeam, participant, TeamMemberRole.MEMBER),
                        teamMember(olderTeam, participant, TeamMemberRole.LEADER)));

        List<TeamApplicationRes> result = teamApplicationService.getMyApplications(10L);

        assertThat(result).containsExactly(
                new TeamApplicationRes(
                        "newer-contest",
                        "AI 창의 경진대회",
                        "SW중심대학사업단",
                        ParticipationType.TEAM,
                        "트랙키 팀",
                        "김참가",
                        "컴퓨터공학부",
                        3,
                        TeamStatus.PENDING,
                        "leader@example.com",
                        "010-1234-5678",
                        "학교 문제를 해결하고 싶습니다.",
                        newerCreatedAt,
                        newerUpdatedAt),
                new TeamApplicationRes(
                        "older-contest",
                        "캠퍼스 아이디어톤",
                        "학생지원처",
                        ParticipationType.INDIVIDUAL,
                        "김참가",
                        "김참가",
                        "컴퓨터공학부",
                        3,
                        TeamStatus.APPROVED,
                        "leader@example.com",
                        "010-1234-5678",
                        "학교 문제를 해결하고 싶습니다.",
                        olderCreatedAt,
                        olderUpdatedAt));
        verify(teamMemberRepository).findAllWithTeamAndContestByUserId(10L);
    }

    @Test
    @DisplayName("본인의 참가 신청이 없으면 빈 목록을 반환한다")
    void getMyApplications_returnsEmptyList() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(org.mockito.Mockito.mock(User.class)));
        given(teamMemberRepository.findAllWithTeamAndContestByUserId(10L))
                .willReturn(List.of());

        assertThat(teamApplicationService.getMyApplications(10L)).isEmpty();
    }

    @Test
    @DisplayName("참가 신청 목록 조회 사용자를 찾을 수 없으면 사용자 없음으로 처리한다")
    void getMyApplications_throwsWhenUserDoesNotExist() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamApplicationService.getMyApplications(10L))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);

        verifyNoInteractions(teamRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("일반 팀원도 신청 접수부터 확정 수상까지 진행 현황을 조회한다")
    void getApplicationProgress_returnsFiveProgressStepsForMember() {
        LocalDateTime appliedAt = LocalDateTime.of(2026, 7, 20, 9, 0);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 7, 22, 18, 30);
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 7, 25, 14, 0);
        LocalDateTime awardedAt = LocalDateTime.of(2026, 7, 27, 10, 0);
        Contest contest = Contest.builder()
                .id(20L)
                .publicId("contest-public-id")
                .status(ContestStatus.AWARDED)
                .build();
        Team team = Team.builder()
                .id(30L)
                .contest(contest)
                .status(TeamStatus.APPROVED)
                .build();
        ReflectionTestUtils.setField(team, "createdAt", appliedAt);
        TeamMember membership = teamMember(
                team,
                User.builder().id(10L).build(),
                TeamMemberRole.MEMBER);
        Submission submission = Submission.builder()
                .id(40L)
                .team(team)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(submittedAt)
                .build();
        ContestStage reviewStage = ContestStage.builder()
                .id(50L)
                .name("2차 심사")
                .sequenceNo(4)
                .build();
        ContestStageEntry entry = ContestStageEntry.builder()
                .contestStage(reviewStage)
                .submission(submission)
                .status(EntryStatus.PASSED)
                .finalizedAt(reviewedAt)
                .build();
        Award award = Award.builder()
                .team(team)
                .awardRankNo(1)
                .prize("대상")
                .status(AwardStatus.CONFIRMED)
                .confirmedAt(awardedAt)
                .build();

        given(teamMemberRepository.findWithTeamAndContestByUserIdAndContestPublicId(
                10L, "contest-public-id"))
                .willReturn(Optional.of(membership));
        given(contestStageRepository.findAllByContestIdAndStageTypeInOrderBySequenceNoAsc(
                20L,
                Set.of(
                        StageType.REVIEW,
                        StageType.PRESENTATION)))
                .willReturn(List.of(reviewStage));
        given(submissionRepository.findByTeamId(30L)).willReturn(Optional.of(submission));
        given(contestStageEntryRepository
                .findAllWithStageBySubmissionIdOrderBySequenceNoAsc(40L))
                .willReturn(List.of(entry));
        given(awardRepository.findFirstByTeamIdAndStatusOrderByAwardRankNoAsc(
                30L, AwardStatus.CONFIRMED))
                .willReturn(Optional.of(award));

        ApplicationProgressRes result =
                teamApplicationService.getApplicationProgress(10L, "contest-public-id");

        assertThat(result.contestPublicId()).isEqualTo("contest-public-id");
        assertThat(result.steps()).containsExactly(
                new Step(
                        StepType.APPLICATION_RECEIVED,
                        "신청 접수",
                        StepStatus.COMPLETED,
                        "접수 완료",
                        appliedAt),
                new Step(
                        StepType.APPLICATION_REVIEW,
                        "신청 검토",
                        StepStatus.COMPLETED,
                        "승인",
                        null),
                new Step(
                        StepType.SUBMISSION,
                        "제출물",
                        StepStatus.COMPLETED,
                        "제출 완료",
                        submittedAt),
                new Step(
                        StepType.REVIEW,
                        "심사",
                        StepStatus.COMPLETED,
                        "2차 심사 통과",
                        reviewedAt),
                new Step(
                        StepType.RESULT,
                        "결과",
                        StepStatus.COMPLETED,
                        "대상",
                        awardedAt));
    }

    @Test
    @DisplayName("이전 심사를 통과했어도 다음 심사가 남아 있으면 심사 진행 중으로 반환한다")
    void getApplicationProgress_waitsForNextReviewStage() {
        Contest contest = Contest.builder()
                .id(20L)
                .publicId("contest-public-id")
                .status(ContestStatus.REVIEWING)
                .build();
        Team team = Team.builder()
                .id(30L)
                .contest(contest)
                .status(TeamStatus.APPROVED)
                .build();
        TeamMember membership = teamMember(
                team,
                User.builder().id(10L).build(),
                TeamMemberRole.MEMBER);
        Submission submission = Submission.builder()
                .id(40L)
                .team(team)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 7, 22, 18, 30))
                .build();
        ContestStage firstReviewStage = ContestStage.builder()
                .id(50L)
                .name("1차 심사")
                .sequenceNo(3)
                .build();
        ContestStage secondReviewStage = ContestStage.builder()
                .id(51L)
                .name("2차 심사")
                .sequenceNo(4)
                .build();
        ContestStageEntry firstEntry = ContestStageEntry.builder()
                .contestStage(firstReviewStage)
                .submission(submission)
                .status(EntryStatus.PASSED)
                .finalizedAt(LocalDateTime.of(2026, 7, 25, 14, 0))
                .build();

        given(teamMemberRepository.findWithTeamAndContestByUserIdAndContestPublicId(
                10L, "contest-public-id"))
                .willReturn(Optional.of(membership));
        given(contestStageRepository.findAllByContestIdAndStageTypeInOrderBySequenceNoAsc(
                20L,
                Set.of(
                        StageType.REVIEW,
                        StageType.PRESENTATION)))
                .willReturn(List.of(firstReviewStage, secondReviewStage));
        given(submissionRepository.findByTeamId(30L)).willReturn(Optional.of(submission));
        given(contestStageEntryRepository
                .findAllWithStageBySubmissionIdOrderBySequenceNoAsc(40L))
                .willReturn(List.of(firstEntry));

        Step reviewStep = teamApplicationService
                .getApplicationProgress(10L, "contest-public-id")
                .steps()
                .get(3);

        assertThat(reviewStep.status()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(reviewStep.description()).isEqualTo("2차 심사 대기");
        assertThat(reviewStep.occurredAt()).isNull();
    }

    @Test
    @DisplayName("제출물과 후속 결과가 없으면 해당 단계를 대기 상태로 반환한다")
    void getApplicationProgress_returnsWaitingStepsBeforeSubmission() {
        LocalDateTime appliedAt = LocalDateTime.of(2026, 7, 20, 9, 0);
        Contest contest = Contest.builder()
                .id(20L)
                .publicId("contest-public-id")
                .status(ContestStatus.REVIEWING)
                .build();
        Team team = Team.builder()
                .id(30L)
                .contest(contest)
                .status(TeamStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(team, "createdAt", appliedAt);
        TeamMember membership = teamMember(
                team,
                User.builder().id(10L).build(),
                TeamMemberRole.LEADER);

        given(teamMemberRepository.findWithTeamAndContestByUserIdAndContestPublicId(
                10L, "contest-public-id"))
                .willReturn(Optional.of(membership));
        given(submissionRepository.findByTeamId(30L)).willReturn(Optional.empty());

        ApplicationProgressRes result =
                teamApplicationService.getApplicationProgress(10L, "contest-public-id");

        assertThat(result.steps())
                .extracting(Step::status)
                .containsExactly(
                        StepStatus.COMPLETED,
                        StepStatus.IN_PROGRESS,
                        StepStatus.WAITING,
                        StepStatus.WAITING,
                        StepStatus.WAITING);
        assertThat(result.steps())
                .extracting(Step::description)
                .containsExactly("접수 완료", "검토 중", "제출 전", "심사 대기", "발표 전");
        verifyNoInteractions(contestStageEntryRepository, awardRepository);
    }

    @Test
    @DisplayName("해당 대회 팀에 속하지 않은 사용자는 진행 현황을 조회할 수 없다")
    void getApplicationProgress_throwsWhenMembershipDoesNotExist() {
        given(teamMemberRepository.findWithTeamAndContestByUserIdAndContestPublicId(
                10L, "contest-public-id"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                teamApplicationService.getApplicationProgress(10L, "contest-public-id"))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_NOT_FOUND);

        verifyNoInteractions(
                submissionRepository,
                contestStageRepository,
                contestStageEntryRepository,
                awardRepository);
    }

    @Test
    @DisplayName("본인이 속한 팀을 참가자 팀 관리 응답으로 매핑한다")
    void getMyTeams_returnsParticipantTeamResponses() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(org.mockito.Mockito.mock(User.class)));
        Team team = application(
                "contest-public-id",
                "AI 창의 경진대회",
                "SW중심대학사업단",
                ParticipationType.TEAM,
                "트랙키 팀",
                TeamStatus.APPROVED,
                LocalDateTime.of(2026, 7, 24, 15, 30),
                LocalDateTime.of(2026, 7, 24, 16, 10));
        ReflectionTestUtils.setField(team, "publicId", "team-public-id");
        TeamMember membership = teamMember(
                team,
                org.mockito.Mockito.mock(User.class),
                TeamMemberRole.LEADER);
        given(teamMemberRepository.findAllWithTeamAndContestByUserId(10L))
                .willReturn(List.of(membership));

        List<ParticipantTeamRes> result = teamApplicationService.getMyTeams(10L);

        assertThat(result).containsExactly(new ParticipantTeamRes(
                "team-public-id",
                "contest-public-id",
                "AI 창의 경진대회",
                "트랙키 팀",
                TeamMemberRole.LEADER,
                3,
                TeamStatus.APPROVED,
                null));
    }

    @Test
    @DisplayName("참가 신청 정보를 정리하고 검토 대기 상태로 저장한다")
    @SuppressWarnings("unchecked")
    void createApplication_savesPendingApplication() {
        User user = givenParticipant(10L, 2L);
        User firstMember = User.builder().id(11L).build();
        User secondMember = User.builder().id(12L).build();
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        given(teamRepository.existsByContestIdAndLeaderUserId(20L, 10L)).willReturn(false);
        given(userRepository.findAllByIdInAndOrganizationIdAndRoleAndStatus(
                List.of(11L, 12L),
                2L,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE))
                .willReturn(List.of(firstMember, secondMember));
        given(teamMemberRepository.existsByTeamContestIdAndUserIdIn(
                20L, List.of(11L, 12L, 10L)))
                .willReturn(false);
        given(teamRepository.save(any(Team.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 12L)));

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());
        Team savedTeam = teamCaptor.getValue();
        assertThat(savedTeam.getContest()).isSameAs(contest);
        assertThat(savedTeam.getLeaderUser()).isSameAs(user);
        assertThat(savedTeam.getName()).isEqualTo("트랙키 팀");
        assertThat(savedTeam.getLeaderName()).isEqualTo("김참가");
        assertThat(savedTeam.getMajor()).isEqualTo("컴퓨터공학부");
        assertThat(savedTeam.getMemberCount()).isEqualTo(3);
        assertThat(savedTeam.getStatus()).isEqualTo(TeamStatus.PENDING);
        assertThat(savedTeam.getContactEmail()).isEqualTo("leader@example.com");
        assertThat(savedTeam.getPhone()).isEqualTo("010-1234-5678");
        assertThat(savedTeam.getMotivation()).isEqualTo("학교 문제를 해결하고 싶습니다.");

        ArgumentCaptor<List<TeamMember>> teamMembersCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(teamMemberRepository).saveAll(teamMembersCaptor.capture());
        assertThat(teamMembersCaptor.getValue())
                .extracting(TeamMember::getUser, TeamMember::getRole, TeamMember::getTeam)
                .containsExactly(
                        tuple(user, TeamMemberRole.LEADER, savedTeam),
                        tuple(firstMember, TeamMemberRole.MEMBER, savedTeam),
                        tuple(secondMember, TeamMemberRole.MEMBER, savedTeam));
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 사용자 없음으로 처리한다")
    void createApplication_throwsWhenUserDoesNotExist() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 12L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);

        verifyNoInteractions(contestRepository, teamRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("같은 학교의 공개 대회를 찾을 수 없으면 대회 없음으로 처리한다")
    void createApplication_throwsWhenContestDoesNotExist() {
        givenParticipant(10L, 2L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 12L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(ContestErrorResponseCode.CONTEST_NOT_FOUND);

        verifyNoInteractions(teamRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("접수가 종료된 대회에는 참가 신청할 수 없다")
    void createApplication_throwsWhenApplicationIsClosed() {
        givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.REVIEWING, ParticipationType.TEAM);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 12L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_NOT_OPEN);

        verifyNoInteractions(teamRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("개인전에 2명 이상으로 신청하면 참가 인원 오류로 처리한다")
    void createApplication_throwsWhenIndividualMemberCountIsInvalid() {
        givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.INDIVIDUAL);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);

        verifyNoInteractions(teamRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("대표자를 제외한 팀원이 4명을 초과하면 참가 인원 오류로 처리한다")
    void createApplication_throwsWhenMemberCountExceedsLimit() {
        givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 12L, 13L, 14L, 15L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);

        verifyNoInteractions(teamRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("같은 대회에 이미 참가 신청했으면 중복 신청으로 처리한다")
    void createApplication_throwsWhenApplicationAlreadyExists() {
        User user = givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        given(teamRepository.existsByContestIdAndLeaderUserId(20L, 10L)).willReturn(true);

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 12L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_ALREADY_EXISTS);

        verify(teamRepository, never()).save(any(Team.class));
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    @DisplayName("대표자를 팀원 목록에 포함하면 유효하지 않은 팀원으로 처리한다")
    void createApplication_throwsWhenLeaderIsIncludedInMembers() {
        User user = givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(10L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_INVALID);

        verify(teamRepository, never()).save(any(Team.class));
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    @DisplayName("중복된 팀원을 포함하면 유효하지 않은 팀원으로 처리한다")
    void createApplication_throwsWhenMemberIdsAreDuplicated() {
        User user = givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 11L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_INVALID);

        verify(teamRepository, never()).save(any(Team.class));
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    @DisplayName("같은 학교의 활성 참가자로 조회되지 않는 팀원이 있으면 신청할 수 없다")
    void createApplication_throwsWhenMemberIsNotEligible() {
        User user = givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        given(teamRepository.existsByContestIdAndLeaderUserId(20L, 10L)).willReturn(false);
        given(userRepository.findAllByIdInAndOrganizationIdAndRoleAndStatus(
                List.of(11L, 12L),
                2L,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE))
                .willReturn(List.of(User.builder().id(11L).build()));

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L, 12L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_INVALID);

        verify(teamRepository, never()).save(any(Team.class));
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    @DisplayName("대표자나 선택 팀원이 같은 대회의 다른 팀에 참여 중이면 신청할 수 없다")
    void createApplication_throwsWhenParticipantAlreadyJoinedContest() {
        User user = givenParticipant(10L, 2L);
        User member = User.builder().id(11L).build();
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        given(teamRepository.existsByContestIdAndLeaderUserId(20L, 10L)).willReturn(false);
        given(userRepository.findAllByIdInAndOrganizationIdAndRoleAndStatus(
                List.of(11L),
                2L,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE))
                .willReturn(List.of(member));
        given(teamMemberRepository.existsByTeamContestIdAndUserIdIn(
                20L, List.of(11L, 10L)))
                .willReturn(true);

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of(11L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING);

        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    @DisplayName("개인전은 대표자 한 명을 LEADER로 저장한다")
    @SuppressWarnings("unchecked")
    void createApplication_savesLeaderForIndividualContest() {
        User user = givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.INDIVIDUAL);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        given(teamRepository.existsByContestIdAndLeaderUserId(20L, 10L)).willReturn(false);
        given(teamMemberRepository.existsByTeamContestIdAndUserIdIn(20L, List.of(10L)))
                .willReturn(false);
        given(teamRepository.save(any(Team.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(List.of()));

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());
        assertThat(teamCaptor.getValue().getMemberCount()).isEqualTo(1);

        ArgumentCaptor<List<TeamMember>> teamMembersCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(teamMemberRepository).saveAll(teamMembersCaptor.capture());
        assertThat(teamMembersCaptor.getValue()).singleElement().satisfies(teamMember -> {
            assertThat(teamMember.getUser()).isSameAs(user);
            assertThat(teamMember.getRole()).isEqualTo(TeamMemberRole.LEADER);
            assertThat(teamMember.getTeam()).isSameAs(teamCaptor.getValue());
        });
    }

    @Test
    @DisplayName("같은 학교의 활성 참가자를 이름 또는 학번 검색 응답으로 매핑한다")
    void searchParticipants_returnsMappedParticipants() {
        givenParticipant(10L, 2L);
        User candidate = User.builder()
                .id(11L)
                .name("김팀원")
                .studentId("20260001")
                .major("컴퓨터공학부")
                .build();
        given(userRepository.searchParticipants(
                2L,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE,
                10L,
                "김",
                PageRequest.of(0, 20)))
                .willReturn(List.of(candidate));

        assertThat(teamApplicationService.searchParticipants(10L, "  김  "))
                .containsExactly(new ParticipantSearchRes(
                        11L,
                        "김팀원",
                        "20260001",
                        "컴퓨터공학부"));
    }

    @Test
    @DisplayName("참가자 검색어가 비어 있으면 전체 목록 대신 빈 목록을 반환한다")
    void searchParticipants_returnsEmptyListWhenKeywordIsBlank() {
        given(userRepository.findById(10L))
                .willReturn(Optional.of(org.mockito.Mockito.mock(User.class)));

        assertThat(teamApplicationService.searchParticipants(10L, "  ")).isEmpty();

        verify(userRepository, never()).searchParticipants(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("참가자 검색 사용자를 찾을 수 없으면 사용자 없음으로 처리한다")
    void searchParticipants_throwsWhenUserDoesNotExist() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamApplicationService.searchParticipants(10L, "김"))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);

        verify(userRepository, never()).searchParticipants(
                any(), any(), any(), any(), any(), any());
    }

    private User givenParticipant(Long userId, Long organizationId) {
        User user = org.mockito.Mockito.mock(User.class);
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(user.getOrganization()).willReturn(organization);
        given(organization.getId()).willReturn(organizationId);
        return user;
    }

    private Contest contest(Long id, ContestStatus status, ParticipationType participationType) {
        return Contest.builder()
                .id(id)
                .publicId("contest-public-id")
                .status(status)
                .participationType(participationType)
                .build();
    }

    private TeamApplicationCreateReq applicationRequest(List<Long> memberUserIds) {
        return new TeamApplicationCreateReq(
                "  트랙키 팀  ",
                "  김참가  ",
                "  컴퓨터공학부  ",
                memberUserIds,
                "  leader@example.com  ",
                "  010-1234-5678  ",
                "  학교 문제를 해결하고 싶습니다.  ");
    }

    private Team application(
            String contestPublicId,
            String contestTitle,
            String department,
            ParticipationType participationType,
            String teamName,
            TeamStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        Contest contest = Contest.builder()
                .publicId(contestPublicId)
                .title(contestTitle)
                .department(department)
                .participationType(participationType)
                .build();
        Team team = Team.builder()
                .contest(contest)
                .name(teamName)
                .leaderName("김참가")
                .major("컴퓨터공학부")
                .memberCount(3)
                .status(status)
                .contactEmail("leader@example.com")
                .phone("010-1234-5678")
                .motivation("학교 문제를 해결하고 싶습니다.")
                .build();
        ReflectionTestUtils.setField(team, "createdAt", createdAt);
        ReflectionTestUtils.setField(team, "updatedAt", updatedAt);
        return team;
    }

    private TeamMember teamMember(Team team, User user, TeamMemberRole role) {
        return TeamMember.builder()
                .team(team)
                .user(user)
                .role(role)
                .build();
    }
}
