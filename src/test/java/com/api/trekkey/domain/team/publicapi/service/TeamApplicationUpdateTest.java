package com.api.trekkey.domain.team.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamRes;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// 참가 신청 수정·내 신청 조회 테스트 (신청 생성 테스트는 TeamApplicationServiceImplTest)
@ExtendWith(MockitoExtension.class)
class TeamApplicationUpdateTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private TeamRepository teamRepository;

    private TeamApplicationServiceImpl teamApplicationService;

    private Contest contest;
    private User leader;

    @BeforeEach
    void setUp() {
        teamApplicationService =
                new TeamApplicationServiceImpl(userRepository, contestRepository, teamRepository);

        contest = mock(Contest.class);
        lenient().when(contest.getParticipationType()).thenReturn(ParticipationType.TEAM);
        lenient().when(contest.getPublicId()).thenReturn("contest-pub-1");
        lenient().when(contest.getTitle()).thenReturn("2026 AI 공모전");
        lenient().when(contest.getStatus()).thenReturn(ContestStatus.APPLICATION_OPEN);

        leader = mock(User.class);
        lenient().when(leader.getId()).thenReturn(10L);
    }

    @Test
    @DisplayName("보완요청 상태에서 수정하면 검토중으로 전환된다")
    void updateApplication_revisionRequestedBecomesPending() {
        Team team = teamFixture(TeamStatus.REVISION_REQUESTED, null);
        given(teamRepository.findByPublicId("team-pub-1")).willReturn(Optional.of(team));

        TeamRes result = teamApplicationService.updateApplication(10L, "team-pub-1", updateReq(3));

        assertThat(result.status()).isEqualTo(TeamStatus.PENDING);
        assertThat(result.name()).isEqualTo("수정된 팀명");
    }

    @Test
    @DisplayName("대표자가 아니면 수정할 수 없다")
    void updateApplication_throwsWhenNotLeader() {
        Team team = teamFixture(TeamStatus.PENDING, null);
        given(teamRepository.findByPublicId("team-pub-1")).willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamApplicationService.updateApplication(99L, "team-pub-1", updateReq(3)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_FORBIDDEN);
    }

    @Test
    @DisplayName("명단이 확정된 팀은 수정할 수 없다")
    void updateApplication_throwsWhenFinalized() {
        Team team = teamFixture(TeamStatus.APPROVED, LocalDateTime.now());
        given(teamRepository.findByPublicId("team-pub-1")).willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamApplicationService.updateApplication(10L, "team-pub-1", updateReq(3)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_ALREADY_FINALIZED);
    }

    @Test
    @DisplayName("개인전 대회는 참가 인원을 1명 초과로 수정할 수 없다")
    void updateApplication_throwsWhenIndividualContestMemberCountInvalid() {
        given(contest.getParticipationType()).willReturn(ParticipationType.INDIVIDUAL);
        Team team = teamFixture(TeamStatus.PENDING, null);
        given(teamRepository.findByPublicId("team-pub-1")).willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamApplicationService.updateApplication(10L, "team-pub-1", updateReq(2)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);
    }

    @Test
    @DisplayName("존재하지 않는 신청 수정 시 TEAM_NOT_FOUND 예외가 발생한다")
    void updateApplication_throwsWhenTeamNotFound() {
        given(teamRepository.findByPublicId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamApplicationService.updateApplication(10L, "unknown", updateReq(3)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("내 신청 목록을 최신순으로 반환한다")
    void getMyApplications_returnsTeamResList() {
        Team team = teamFixture(TeamStatus.PENDING, null);
        given(teamRepository.findAllByLeaderUserIdOrderByCreatedAtDesc(10L)).willReturn(List.of(team));

        List<TeamRes> result = teamApplicationService.getMyApplications(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).contestTitle()).isEqualTo("2026 AI 공모전");
        assertThat(result.get(0).id()).isEqualTo("team-pub-1");
    }

    //======= 헬퍼 메서드 ==========

    private Team teamFixture(TeamStatus status, LocalDateTime finalizedAt) {
        Team team = Team.builder()
                .publicId("team-pub-1")
                .contest(contest)
                .leaderUser(leader)
                .name("원래 팀명")
                .leaderName("김하린")
                .major("컴퓨터공학부")
                .memberCount(3)
                .status(status)
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

    private TeamApplicationUpdateReq updateReq(int memberCount) {
        return new TeamApplicationUpdateReq(
                "수정된 팀명", "김하린", "컴퓨터공학부", memberCount,
                "harin@hansung.ac.kr", "010-1234-5678", "수정된 동기");
    }
}
