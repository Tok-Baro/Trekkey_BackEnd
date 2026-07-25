package com.api.trekkey.domain.credential.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.service.CredentialIssuanceService;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.api.trekkey.domain.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AwardCredentialIssuerTest {

    @Mock
    private CredentialIssuanceService credentialIssuanceService;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

    private AwardCredentialIssuer awardCredentialIssuer;

    private Award award;
    private Team team;

    @BeforeEach
    void setUp() {
        awardCredentialIssuer = new AwardCredentialIssuer(
                credentialIssuanceService, teamMemberRepository, submissionFileRepository, new ObjectMapper());

        Organization organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(1L);

        Contest contest = mock(Contest.class);
        lenient().when(contest.getOrganization()).thenReturn(organization);
        lenient().when(contest.getTitle()).thenReturn("2026 캡스톤 경진대회");

        User leader = mock(User.class);
        lenient().when(leader.getId()).thenReturn(10L);

        team = mock(Team.class);
        lenient().when(team.getId()).thenReturn(20L);
        lenient().when(team.getPublicId()).thenReturn("team-pub-1");
        lenient().when(team.getName()).thenReturn("팀트레키");
        lenient().when(team.getMajor()).thenReturn("컴퓨터공학과");
        lenient().when(team.getLeaderName()).thenReturn("이준수");
        lenient().when(team.getLeaderUser()).thenReturn(leader);
        lenient().when(team.getContest()).thenReturn(contest);

        Submission submission = mock(Submission.class);
        lenient().when(submission.getId()).thenReturn(30L);
        lenient().when(submission.getTitle()).thenReturn("AI 작품");

        ContestStageEntry entry = mock(ContestStageEntry.class);
        lenient().when(entry.getSubmission()).thenReturn(submission);
        lenient().when(entry.getFinalScore()).thenReturn(new BigDecimal("87.5"));

        award = mock(Award.class);
        lenient().when(award.getId()).thenReturn(40L);
        lenient().when(award.getPublicId()).thenReturn("award-pub-1");
        lenient().when(award.getCertificateNo()).thenReturn("2026-C1-001");
        lenient().when(award.getPrize()).thenReturn("대상");
        lenient().when(award.getAwardRankNo()).thenReturn(1);
        lenient().when(award.getConfirmedAt()).thenReturn(LocalDateTime.of(2026, 7, 20, 12, 0));
        lenient().when(award.getTeam()).thenReturn(team);
        lenient().when(award.getContestStageEntry()).thenReturn(entry);

        SubmissionFile file = mock(SubmissionFile.class);
        lenient().when(file.getOriginalName()).thenReturn("제안서.pdf");
        lenient().when(file.getContentType()).thenReturn("application/pdf");
        lenient().when(file.getSizeBytes()).thenReturn(1024L);
        lenient().when(file.getSha256()).thenReturn("a".repeat(64));
        lenient().when(submissionFileRepository.findAllBySubmissionId(30L)).thenReturn(List.of(file));
        lenient().when(teamMemberRepository.findAllByTeamId(20L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("수상 확정 사실이 상장번호·스냅샷·팀/대표 주체·파일 SHA-256과 함께 발급 명령으로 변환된다")
    void issueForConfirmedAward_mapsAwardFactsIntoCommand() {
        awardCredentialIssuer.issueForConfirmedAward(award);

        ArgumentCaptor<CredentialIssueCommand> captor = ArgumentCaptor.forClass(CredentialIssueCommand.class);
        verify(credentialIssuanceService).issue(captor.capture());
        CredentialIssueCommand command = captor.getValue();

        assertThat(command.issuerOrganizationId()).isEqualTo(1L);
        assertThat(command.credentialNo()).isEqualTo("2026-C1-001");
        assertThat(command.credentialType()).isEqualTo(CredentialType.AWARD);
        assertThat(command.schemaProfileId()).isEqualTo(CredentialSchemaProfiles.AWARD_V1);

        assertThat(command.source().sourceType()).isEqualTo(CredentialSourceType.AWARD);
        assertThat(command.source().awardId()).isEqualTo(40L);
        assertThat(command.source().publicId()).isEqualTo("award-pub-1");
        assertThat(command.source().snapshot().get("prize").asText()).isEqualTo("대상");
        assertThat(command.source().snapshot().get("finalScore").asText()).isEqualTo("87.5");

        assertThat(command.subjects()).hasSize(2);
        assertThat(command.subjects().get(0).subjectType()).isEqualTo(CredentialSubjectType.TEAM);
        assertThat(command.subjects().get(0).displayName()).isEqualTo("팀트레키");
        assertThat(command.subjects().get(1).subjectType()).isEqualTo(CredentialSubjectType.USER);
        assertThat(command.subjects().get(1).userId()).isEqualTo(10L);
        assertThat(command.subjects().get(1).roleCode()).isEqualTo("REPRESENTATIVE");

        assertThat(command.files()).hasSize(1);
        assertThat(command.files().get(0).sha256Hex()).isEqualTo("0x" + "a".repeat(64));
    }
}
