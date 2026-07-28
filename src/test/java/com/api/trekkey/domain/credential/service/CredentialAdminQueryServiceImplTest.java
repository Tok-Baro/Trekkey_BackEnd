package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.repository.AncCredentialSourceRepository;
import com.api.trekkey.domain.credential.repository.CredentialSummaryRow;
import com.api.trekkey.domain.credential.web.dto.CredentialSummaryRes;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
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

@ExtendWith(MockitoExtension.class)
class CredentialAdminQueryServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private AncCredentialSourceRepository credentialSourceRepository;

    private CredentialAdminQueryServiceImpl credentialAdminQueryService;

    private Organization organization;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        credentialAdminQueryService = new CredentialAdminQueryServiceImpl(
                userRepository, contestRepository, teamRepository, credentialSourceRepository);

        organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(1L);

        admin = mock(User.class);
        lenient().when(admin.getOrganization()).thenReturn(organization);
        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(admin));

        contest = mock(Contest.class);
        lenient().when(contest.getId()).thenReturn(200L);
        lenient().when(contest.getOrganization()).thenReturn(organization);
        lenient().when(contestRepository.findByPublicId("contest-pub-1")).thenReturn(Optional.of(contest));
    }

    @Test
    @DisplayName("대회 발급 현황이 3종 타입과 함께 조회된다")
    void getContestCredentials_returnsSummaries() {
        CredentialSummaryRow award = rowFixture("2026-C1-001", "AWARD", "AWARD");
        CredentialSummaryRow work = rowFixture("2026-W1-T1", "WORK", "SUBMISSION");
        given(credentialSourceRepository.findSummaryRowsByContestId(200L))
                .willReturn(List.of(award, work));

        List<CredentialSummaryRes> result =
                credentialAdminQueryService.getContestCredentials(100L, "contest-pub-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).credentialType()).isEqualTo(CredentialType.AWARD);
        assertThat(result.get(0).sourceType()).isEqualTo(CredentialSourceType.AWARD);
        assertThat(result.get(0).status()).isEqualTo(CredentialStatus.READY);
        assertThat(result.get(1).credentialNo()).isEqualTo("2026-W1-T1");
        assertThat(result.get(1).teamName()).isEqualTo("팀트레키");
    }

    @Test
    @DisplayName("타 조직 대회는 404로 비노출한다")
    void getContestCredentials_throwsWhenOtherOrganization() {
        Organization other = mock(Organization.class);
        lenient().when(other.getId()).thenReturn(9L);
        given(contest.getOrganization()).willReturn(other);

        assertThatThrownBy(() -> credentialAdminQueryService.getContestCredentials(100L, "contest-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_NOT_FOUND);
    }

    @Test
    @DisplayName("타 조직 팀은 404로 비노출한다")
    void getTeamCredentials_throwsWhenOtherOrganization() {
        Organization other = mock(Organization.class);
        lenient().when(other.getId()).thenReturn(9L);
        Contest otherContest = mock(Contest.class);
        lenient().when(otherContest.getOrganization()).thenReturn(other);
        Team team = mock(Team.class);
        lenient().when(team.getContest()).thenReturn(otherContest);
        given(teamRepository.findByPublicId("team-pub-1")).willReturn(Optional.of(team));

        assertThatThrownBy(() -> credentialAdminQueryService.getTeamCredentials(100L, "team-pub-1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(TeamErrorResponseCode.TEAM_NOT_FOUND);
    }

    //======= 헬퍼 메서드 ==========

    private CredentialSummaryRow rowFixture(String credentialNo, String type, String sourceType) {
        CredentialSummaryRow row = mock(CredentialSummaryRow.class);
        lenient().when(row.getCredentialPublicId()).thenReturn("cred-" + credentialNo);
        lenient().when(row.getCredentialNo()).thenReturn(credentialNo);
        lenient().when(row.getCredentialType()).thenReturn(type);
        lenient().when(row.getStatus()).thenReturn("READY");
        lenient().when(row.getSourceType()).thenReturn(sourceType);
        lenient().when(row.getTeamPublicId()).thenReturn("team-pub-1");
        lenient().when(row.getTeamName()).thenReturn("팀트레키");
        lenient().when(row.getIssuedAt()).thenReturn(LocalDateTime.of(2026, 7, 27, 12, 0));
        return row;
    }
}
