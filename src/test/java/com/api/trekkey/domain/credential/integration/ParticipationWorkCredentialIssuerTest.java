package com.api.trekkey.domain.credential.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.service.CredentialIssuanceService;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.api.trekkey.domain.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ParticipationWorkCredentialIssuerTest {

    @Mock
    private CredentialIssuanceService credentialIssuanceService;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

    private ParticipationCredentialIssuer participationCredentialIssuer;
    private WorkCredentialIssuer workCredentialIssuer;

    private Team team;
    private Submission submission;

    @BeforeEach
    void setUp() {
        CredentialSubjectAssembler assembler =
                new CredentialSubjectAssembler(teamMemberRepository, submissionFileRepository);
        participationCredentialIssuer =
                new ParticipationCredentialIssuer(credentialIssuanceService, assembler, new ObjectMapper());
        workCredentialIssuer =
                new WorkCredentialIssuer(credentialIssuanceService, assembler, new ObjectMapper());

        Organization organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(1L);

        Contest contest = mock(Contest.class);
        lenient().when(contest.getId()).thenReturn(7L);
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
        lenient().when(team.getMemberCount()).thenReturn(2);
        lenient().when(team.getParticipationFinalizedAt())
                .thenReturn(LocalDateTime.of(2026, 7, 27, 10, 0));

        //확정 명단: 리더 + 팀원 1명 (리더는 subject 중복 제거 검증용)
        User member = mock(User.class);
        lenient().when(member.getId()).thenReturn(11L);
        lenient().when(member.getName()).thenReturn("김팀원");
        lenient().when(member.getMajor()).thenReturn("소프트웨어학과");
        TeamMember leaderRow = mock(TeamMember.class);
        lenient().when(leaderRow.getUser()).thenReturn(leader);
        TeamMember memberRow = mock(TeamMember.class);
        lenient().when(memberRow.getUser()).thenReturn(member);
        lenient().when(teamMemberRepository.findAllByTeamId(20L))
                .thenReturn(List.of(leaderRow, memberRow));

        submission = mock(Submission.class);
        lenient().when(submission.getId()).thenReturn(30L);
        lenient().when(submission.getPublicId()).thenReturn("sub-pub-1");
        lenient().when(submission.getTitle()).thenReturn("AI 작품");
        lenient().when(submission.getTeam()).thenReturn(team);
        lenient().when(submission.getFinalizedAt())
                .thenReturn(LocalDateTime.of(2026, 7, 27, 11, 0));

        SubmissionFile file = mock(SubmissionFile.class);
        lenient().when(file.getOriginalName()).thenReturn("작품.pdf");
        lenient().when(file.getContentType()).thenReturn("application/pdf");
        lenient().when(file.getSizeBytes()).thenReturn(1024L);
        lenient().when(file.getSha256()).thenReturn("b".repeat(64));
        lenient().when(submissionFileRepository.findAllBySubmissionId(30L)).thenReturn(List.of(file));
    }

    @Test
    @DisplayName("명단 확정 팀이 참여 Credential 명령으로 변환된다 — 팀+대표+팀원 전원, 파일 증거 없음")
    void issueForFinalizedTeam_mapsRosterIntoCommand() {
        participationCredentialIssuer.issueForFinalizedTeam(team);

        ArgumentCaptor<CredentialIssueCommand> captor = ArgumentCaptor.forClass(CredentialIssueCommand.class);
        verify(credentialIssuanceService).issue(captor.capture());
        CredentialIssueCommand command = captor.getValue();

        assertThat(command.credentialType()).isEqualTo(CredentialType.PARTICIPATION);
        assertThat(command.schemaProfileId()).isEqualTo(CredentialSchemaProfiles.PARTICIPATION_V1);
        assertThat(command.credentialNo()).isEqualTo("2026-P7-T20");

        assertThat(command.source().sourceType()).isEqualTo(CredentialSourceType.TEAM);
        assertThat(command.source().teamId()).isEqualTo(20L);
        assertThat(command.source().submissionId()).isNull(); //다형성 FK — 타입에 맞는 대상만 non-null
        assertThat(command.source().publicId()).isEqualTo("team-pub-1");

        //팀 + 대표 + 팀원(리더 중복 제거) = 3 subjects, 팀원 roleCode는 PARTICIPANT
        assertThat(command.subjects()).hasSize(3);
        assertThat(command.subjects().get(0).subjectType()).isEqualTo(CredentialSubjectType.TEAM);
        assertThat(command.subjects().get(1).roleCode()).isEqualTo("REPRESENTATIVE");
        assertThat(command.subjects().get(2).userId()).isEqualTo(11L);
        assertThat(command.subjects().get(2).roleCode()).isEqualTo("PARTICIPANT");

        assertThat(command.files()).isEmpty();
    }

    @Test
    @DisplayName("확정 제출물이 작품 Credential 명령으로 변환된다 — 파일 SHA-256 증거 포함")
    void issueForFinalizedSubmission_mapsSubmissionIntoCommand() {
        workCredentialIssuer.issueForFinalizedSubmission(submission);

        ArgumentCaptor<CredentialIssueCommand> captor = ArgumentCaptor.forClass(CredentialIssueCommand.class);
        verify(credentialIssuanceService).issue(captor.capture());
        CredentialIssueCommand command = captor.getValue();

        assertThat(command.credentialType()).isEqualTo(CredentialType.WORK);
        assertThat(command.schemaProfileId()).isEqualTo(CredentialSchemaProfiles.WORK_V1);
        assertThat(command.credentialNo()).isEqualTo("2026-W7-T20");

        assertThat(command.source().sourceType()).isEqualTo(CredentialSourceType.SUBMISSION);
        assertThat(command.source().submissionId()).isEqualTo(30L);
        assertThat(command.source().teamId()).isNull();
        assertThat(command.source().publicId()).isEqualTo("sub-pub-1");
        assertThat(command.source().snapshot().get("submissionTitle").asText()).isEqualTo("AI 작품");

        assertThat(command.subjects()).hasSize(3);
        assertThat(command.files()).hasSize(1);
        assertThat(command.files().get(0).sha256Hex()).isEqualTo("0x" + "b".repeat(64));
    }
}
