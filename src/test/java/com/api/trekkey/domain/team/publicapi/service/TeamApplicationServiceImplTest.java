package com.api.trekkey.domain.team.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private TeamApplicationServiceImpl teamApplicationService;

    @Test
    @DisplayName("참가 신청 정보를 정리하고 검토 대기 상태로 저장한다")
    void createApplication_savesPendingApplication() {
        User user = givenParticipant(10L, 2L);
        Contest contest = contest(20L, ContestStatus.APPLICATION_OPEN, ParticipationType.TEAM);
        given(user.getId()).willReturn(10L);
        given(contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                "contest-public-id",
                2L,
                PUBLIC_STATUSES))
                .willReturn(Optional.of(contest));
        given(teamRepository.existsByContestIdAndLeaderUserId(20L, 10L)).willReturn(false);

        teamApplicationService.createApplication(10L, "contest-public-id", applicationRequest(3));

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
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 사용자 없음으로 처리한다")
    void createApplication_throwsWhenUserDoesNotExist() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamApplicationService.createApplication(
                10L,
                "contest-public-id",
                applicationRequest(3)))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);

        verifyNoInteractions(contestRepository, teamRepository);
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
                applicationRequest(3)))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(ContestErrorResponseCode.CONTEST_NOT_FOUND);

        verifyNoInteractions(teamRepository);
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
                applicationRequest(3)))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_NOT_OPEN);

        verifyNoInteractions(teamRepository);
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
                applicationRequest(2)))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);

        verifyNoInteractions(teamRepository);
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
                applicationRequest(3)))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_ALREADY_EXISTS);

        verify(teamRepository, never()).save(any(Team.class));
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

    private TeamApplicationCreateReq applicationRequest(int memberCount) {
        return new TeamApplicationCreateReq(
                "  트랙키 팀  ",
                "  김참가  ",
                "  컴퓨터공학부  ",
                memberCount,
                "  leader@example.com  ",
                "  010-1234-5678  ",
                "  학교 문제를 해결하고 싶습니다.  ");
    }
}
