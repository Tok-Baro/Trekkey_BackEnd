package com.api.trekkey.domain.team.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.credential.integration.ParticipationCredentialIssuer;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminListRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamStatusUpdateReq;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamAdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ParticipationCredentialIssuer participationCredentialIssuer;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private TeamAdminServiceImpl teamAdminService;

    private Organization organization;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        teamAdminService = new TeamAdminServiceImpl(
                userRepository, contestRepository, teamRepository,
                participationCredentialIssuer, Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC),
                adminAuditLogger);

        organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(1L);

        admin = mock(User.class);
        lenient().when(admin.getId()).thenReturn(100L);
        lenient().when(admin.getOrganization()).thenReturn(organization);
        lenient().when(admin.getRole()).thenReturn(UserRole.ADMIN);
        lenient().when(admin.getStatus()).thenReturn(UserStatus.ACTIVE);

        contest = mock(Contest.class);
        lenient().when(contest.getId()).thenReturn(200L);
        lenient().when(contest.getOrganization()).thenReturn(organization);
        lenient().when(contest.getPublicId()).thenReturn("contest-pub-1");
        lenient().when(contest.getTitle()).thenReturn("2026 AI 공모전");
        lenient().when(contest.getStatus()).thenReturn(ContestStatus.APPLICATION_OPEN);

        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(admin));
    }

    @Test
    @DisplayName("대회별 신청 목록을 상태 필터와 상태별 카운트와 함께 반환한다")
    void getTeams_returnsFilteredListWithStatusCounts() {
        given(contestRepository.findByPublicId("contest-pub-1")).willReturn(Optional.of(contest));
        given(teamRepository.findAllByContestIdOrderByCreatedAtDesc(200L)).willReturn(List.of(
                teamFixture(1L, TeamStatus.PENDING, null),
                teamFixture(2L, TeamStatus.APPROVED, null),
                teamFixture(3L, TeamStatus.APPROVED, null)));

        TeamAdminListRes result = teamAdminService.getTeams(100L, "contest-pub-1", TeamStatus.APPROVED);

        assertThat(result.content()).hasSize(2);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.statusCounts().get(TeamStatus.PENDING)).isEqualTo(1);
        assertThat(result.statusCounts().get(TeamStatus.APPROVED)).isEqualTo(2);
    }

    @Test
    @DisplayName("DB에서 비활성화된 관리자는 남은 JWT로 팀 확정 작업을 수행할 수 없다")
    void finalizeParticipation_rejectsInactiveAdmin() {
        given(admin.getStatus()).willReturn(UserStatus.INACTIVE);

        assertThatThrownBy(() ->
                teamAdminService.finalizeParticipation(
                        100L,
                        "team-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e ->
                        ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        UserErrorResponseCode.USER_INVALID_TOKEN);

        verifyNoInteractions(
                teamRepository,
                participationCredentialIssuer);
    }

    @Test
    @DisplayName("타 조직 대회의 신청 목록은 조회할 수 없다")
    void getTeams_throwsWhenOtherOrganization() {
        Organization otherOrganization = mock(Organization.class);
        given(otherOrganization.getId()).willReturn(2L);
        given(contest.getOrganization()).willReturn(otherOrganization);
        given(contestRepository.findByPublicId("contest-pub-1")).willReturn(Optional.of(contest));

        assertThatThrownBy(() -> teamAdminService.getTeams(100L, "contest-pub-1", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_NOT_FOUND);
    }

    @Test
    @DisplayName("신청 상태를 변경하면 감사 로그가 기록된다")
    void changeStatus_changesAndAudits() {
        Team team = teamFixture(1L, TeamStatus.PENDING, null);
        given(teamRepository.findByPublicIdForUpdate("team-pub-1"))
                .willReturn(Optional.of(team));

        TeamAdminRes result = teamAdminService.changeStatus(
                100L, "team-pub-1", new TeamStatusUpdateReq(TeamStatus.APPROVED));

        assertThat(result.status()).isEqualTo(TeamStatus.APPROVED);
        verify(adminAuditLogger).log(eq(100L), eq(1L), eq(AuditAction.TEAM_STATUS_CHANGE),
                eq("TEAM"), eq(1L), eq("status: PENDING→APPROVED"));
    }

    @Test
    @DisplayName("타 조직 팀은 존재 여부를 노출하지 않고 TEAM_NOT_FOUND로 응답한다")
    void changeStatus_throwsNotFoundWhenOtherOrganization() {
        Organization otherOrganization = mock(Organization.class);
        given(otherOrganization.getId()).willReturn(2L);
        given(contest.getOrganization()).willReturn(otherOrganization);
        Team team = teamFixture(1L, TeamStatus.PENDING, null);
        given(teamRepository.findByPublicIdForUpdate("team-pub-1"))
                .willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamAdminService.changeStatus(
                100L, "team-pub-1", new TeamStatusUpdateReq(TeamStatus.APPROVED)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("명단이 확정된 팀은 리뷰 원장과 어긋나지 않도록 신청 상태를 바꿀 수 없다")
    void changeStatus_rejectsFinalizedTeamStatusChange() {
        Team team = teamFixture(
                1L,
                TeamStatus.APPROVED,
                LocalDateTime.of(2026, 7, 27, 12, 0));
        given(teamRepository.findByPublicIdForUpdate("team-pub-1"))
                .willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamAdminService.changeStatus(
                100L,
                "team-pub-1",
                new TeamStatusUpdateReq(TeamStatus.REJECTED)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e ->
                        ((CustomException) e).getBaseResponseCode())
                .isEqualTo(
                        TeamErrorResponseCode.TEAM_ALREADY_FINALIZED);

        assertThat(team.getStatus()).isEqualTo(TeamStatus.APPROVED);
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("승인된 팀의 명단을 확정하면 잠금 시각이 기록되고 감사 로그가 남는다")
    void finalizeParticipation_finalizesApprovedTeam() {
        Team team = teamFixture(1L, TeamStatus.APPROVED, null);
        given(teamRepository.findByPublicIdForUpdate("team-pub-1"))
                .willReturn(Optional.of(team));

        TeamAdminRes result = teamAdminService.finalizeParticipation(100L, "team-pub-1");

        assertThat(result.participationFinalizedAt()).isNotNull();
        assertThat(team.isFinalized()).isTrue();
        verify(adminAuditLogger).log(eq(100L), eq(1L), eq(AuditAction.TEAM_FINALIZE),
                eq("TEAM"), eq(1L), anyString());
        verify(participationCredentialIssuer).issueForFinalizedTeam(team); //확정과 같은 트랜잭션에서 참여 Credential 발급
    }

    @Test
    @DisplayName("승인되지 않은 팀은 명단을 확정할 수 없다")
    void finalizeParticipation_throwsWhenNotApproved() {
        Team team = teamFixture(1L, TeamStatus.PENDING, null);
        given(teamRepository.findByPublicIdForUpdate("team-pub-1"))
                .willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamAdminService.finalizeParticipation(100L, "team-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_NOT_APPROVED);
    }

    @Test
    @DisplayName("이미 확정된 팀은 다시 확정할 수 없다")
    void finalizeParticipation_throwsWhenAlreadyFinalized() {
        Team team = teamFixture(1L, TeamStatus.APPROVED, LocalDateTime.now());
        given(teamRepository.findByPublicIdForUpdate("team-pub-1"))
                .willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamAdminService.finalizeParticipation(100L, "team-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_ALREADY_FINALIZED);
    }

    //======= 헬퍼 메서드 ==========

    private Team teamFixture(Long id, TeamStatus status, LocalDateTime finalizedAt) {
        User leader = mock(User.class);
        Team team = Team.builder()
                .publicId("team-pub-" + id)
                .contest(contest)
                .leaderUser(leader)
                .name("팀" + id)
                .leaderName("대표" + id)
                .major("컴퓨터공학부")
                .memberCount(3)
                .status(status)
                .contactEmail("team" + id + "@hansung.ac.kr")
                .phone("010-0000-0000")
                .motivation("동기")
                .build();
        ReflectionTestUtils.setField(team, "id", id);
        if (finalizedAt != null) {
            ReflectionTestUtils.setField(team, "participationFinalizedAt", finalizedAt);
        }
        return team;
    }
}
