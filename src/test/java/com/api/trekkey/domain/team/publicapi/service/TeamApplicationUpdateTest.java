package com.api.trekkey.domain.team.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamApplicationUpdateTest {

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
    private ReviewRoundRepository reviewRoundRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private AwardRepository awardRepository;

    @Mock
    private Organization organization;

    private TeamApplicationServiceImpl teamApplicationService;

    private Contest contest;
    private User leader;

    @BeforeEach
    void setUp() {
        teamApplicationService = new TeamApplicationServiceImpl(
                userRepository,
                contestRepository,
                teamRepository,
                teamMemberRepository,
                submissionRepository,
                reviewRoundRepository,
                reviewRoundEntryRepository,
                awardRepository);

        contest = Contest.builder()
                .id(20L)
                .publicId("contest-public-id")
                .participationType(ParticipationType.TEAM)
                .build();
        leader = User.builder()
                .id(10L)
                .organization(organization)
                .build();
    }

    @Test
    @DisplayName("보완요청 상태에서 수정하면 검토중으로 전환된다")
    void updateApplication_revisionRequestedBecomesPending() {
        Team team = teamFixture(TeamStatus.REVISION_REQUESTED, null);
        given(teamRepository.findByContestPublicIdAndLeaderUserIdForUpdate("contest-public-id", 10L))
                .willReturn(Optional.of(team));

        teamApplicationService.updateApplication(
                10L,
                "contest-public-id",
                updateReq(List.of()));

        assertThat(team.getStatus()).isEqualTo(TeamStatus.PENDING);
        assertThat(team.getRevisionReason()).isNull();
        assertThat(team.getName()).isEqualTo("수정된 팀명");
        assertThat(team.getMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("대표자가 아닌 사용자는 신청을 찾을 수 없어 수정할 수 없다")
    void updateApplication_throwsNotFoundWhenUserIsNotLeader() {
        given(teamRepository.findByContestPublicIdAndLeaderUserIdForUpdate("contest-public-id", 99L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> teamApplicationService.updateApplication(
                99L,
                "contest-public-id",
                updateReq(List.of())))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_NOT_FOUND);

        verifyNoInteractions(userRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("명단이 확정된 팀은 신청을 수정할 수 없다")
    void updateApplication_throwsWhenFinalized() {
        Team team = teamFixture(TeamStatus.APPROVED, LocalDateTime.now());
        given(teamRepository.findByContestPublicIdAndLeaderUserIdForUpdate("contest-public-id", 10L))
                .willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamApplicationService.updateApplication(
                10L,
                "contest-public-id",
                updateReq(List.of())))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_ALREADY_FINALIZED);

        verifyNoInteractions(userRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("개인전 신청에는 대표자 외 팀원을 추가할 수 없다")
    void updateApplication_throwsWhenIndividualContestHasMember() {
        contest = Contest.builder()
                .id(20L)
                .publicId("contest-public-id")
                .participationType(ParticipationType.INDIVIDUAL)
                .build();
        Team team = teamFixture(TeamStatus.PENDING, null);
        given(teamRepository.findByContestPublicIdAndLeaderUserIdForUpdate("contest-public-id", 10L))
                .willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamApplicationService.updateApplication(
                10L,
                "contest-public-id",
                updateReq(List.of(11L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);

        verifyNoInteractions(userRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("팀원 수정은 기존 팀원을 유지하고 새 팀원만 추가한다")
    @SuppressWarnings("unchecked")
    void updateApplication_addsMembersWithoutRemovingExistingMembers() {
        Team team = teamFixture(TeamStatus.PENDING, null);
        User omittedUser = User.builder().id(11L).build();
        User retainedUser = User.builder().id(12L).build();
        User addedUser = User.builder().id(13L).build();
        TeamMember omittedMember = teamMember(team, omittedUser);
        TeamMember retainedMember = teamMember(team, retainedUser);
        given(organization.getId()).willReturn(2L);
        given(teamRepository.findByContestPublicIdAndLeaderUserIdForUpdate("contest-public-id", 10L))
                .willReturn(Optional.of(team));
        given(userRepository.findAllByIdInAndOrganizationIdAndRoleAndStatus(
                List.of(12L, 13L),
                2L,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE))
                .willReturn(List.of(retainedUser, addedUser));
        given(teamMemberRepository.existsByTeamIdAndUserId(1L, 10L)).willReturn(true);
        given(teamMemberRepository.findAllByTeamIdAndRole(1L, TeamMemberRole.MEMBER))
                .willReturn(List.of(omittedMember, retainedMember));

        teamApplicationService.updateApplication(
                10L,
                "contest-public-id",
                updateReq(List.of(12L, 13L)));

        verify(teamMemberRepository).existsByContestIdAndUserIdInAndTeamIdNot(
                20L,
                List.of(12L, 13L, 10L),
                1L);
        verify(teamMemberRepository, never()).deleteAll(any());
        ArgumentCaptor<List<TeamMember>> addedMembersCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(teamMemberRepository).saveAll(addedMembersCaptor.capture());
        assertThat(addedMembersCaptor.getValue()).singleElement().satisfies(addedMember -> {
            assertThat(addedMember.getTeam()).isSameAs(team);
            assertThat(addedMember.getUser()).isSameAs(addedUser);
            assertThat(addedMember.getRole()).isEqualTo(TeamMemberRole.MEMBER);
        });
        verify(teamMemberRepository, never()).save(any(TeamMember.class));
        assertThat(team.getMemberCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("기존 팀원과 추가 요청의 합계가 최대 인원을 넘으면 거부한다")
    void updateApplication_throwsWhenFinalMemberCountExceedsLimit() {
        Team team = teamFixture(TeamStatus.PENDING, null);
        User addedUser = User.builder().id(15L).build();
        given(organization.getId()).willReturn(2L);
        given(teamRepository.findByContestPublicIdAndLeaderUserIdForUpdate("contest-public-id", 10L))
                .willReturn(Optional.of(team));
        given(userRepository.findAllByIdInAndOrganizationIdAndRoleAndStatus(
                List.of(15L),
                2L,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE))
                .willReturn(List.of(addedUser));
        given(teamMemberRepository.findAllByTeamIdAndRole(1L, TeamMemberRole.MEMBER))
                .willReturn(List.of(
                        teamMember(team, User.builder().id(11L).build()),
                        teamMember(team, User.builder().id(12L).build()),
                        teamMember(team, User.builder().id(13L).build()),
                        teamMember(team, User.builder().id(14L).build())));

        assertThatThrownBy(() -> teamApplicationService.updateApplication(
                10L,
                "contest-public-id",
                updateReq(List.of(15L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);

        verify(teamMemberRepository, never()).save(any(TeamMember.class));
        verify(teamMemberRepository, never()).saveAll(any());
        assertThat(team.getMemberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("선택한 팀원이 같은 대회의 다른 팀에 참여 중이면 수정할 수 없다")
    void updateApplication_throwsWhenMemberAlreadyParticipatesInOtherTeam() {
        Team team = teamFixture(TeamStatus.PENDING, null);
        User member = User.builder().id(11L).build();
        given(organization.getId()).willReturn(2L);
        given(teamRepository.findByContestPublicIdAndLeaderUserIdForUpdate("contest-public-id", 10L))
                .willReturn(Optional.of(team));
        given(userRepository.findAllByIdInAndOrganizationIdAndRoleAndStatus(
                List.of(11L),
                2L,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE))
                .willReturn(List.of(member));
        given(teamMemberRepository.existsByContestIdAndUserIdInAndTeamIdNot(
                20L,
                List.of(11L, 10L),
                1L))
                .willReturn(true);

        assertThatThrownBy(() -> teamApplicationService.updateApplication(
                10L,
                "contest-public-id",
                updateReq(List.of(11L))))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING);

        verify(teamMemberRepository, never())
                .findAllByTeamIdAndRole(any(), any());
        verify(teamMemberRepository, never()).deleteAll(any());
        verify(teamMemberRepository, never()).saveAll(any());
        assertThat(team.getMemberCount()).isEqualTo(3);
    }

    private Team teamFixture(TeamStatus status, LocalDateTime finalizedAt) {
        Team team = Team.builder()
                .publicId("team-public-id")
                .contest(contest)
                .leaderUser(leader)
                .name("원래 팀명")
                .leaderName("김하린")
                .major("컴퓨터공학부")
                .memberCount(3)
                .status(status)
                .revisionReason(status == TeamStatus.REVISION_REQUESTED
                        ? "연락처를 확인해주세요."
                        : null)
                .contactEmail("harin@hansung.ac.kr")
                .phone("010-1234-5678")
                .motivation("동기")
                .build();
        ReflectionTestUtils.setField(team, "id", 1L);
        if (finalizedAt != null) {
            ReflectionTestUtils.setField(team, "participationFinalizedAt", finalizedAt);
        }
        return team;
    }

    private TeamMember teamMember(Team team, User user) {
        return TeamMember.builder()
                .team(team)
                .user(user)
                .role(TeamMemberRole.MEMBER)
                .build();
    }

    private TeamApplicationUpdateReq updateReq(List<Long> memberUserIds) {
        return new TeamApplicationUpdateReq(
                "  수정된 팀명  ",
                "  김하린  ",
                "  컴퓨터공학부  ",
                memberUserIds,
                "  harin@hansung.ac.kr  ",
                "  010-1234-5678  ",
                "  수정된 동기  ");
    }
}
