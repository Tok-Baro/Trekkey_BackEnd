package com.api.trekkey.domain.credential.integration;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.api.trekkey.domain.credential.service.CredentialIssuanceService;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.IssuedCredential;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 업무 원장 → Credential 발급 연결 어댑터 (erd-mvp §6).
 * 수상 확정(CONFIRMED AWARD)을 원천으로 수상 Credential을 발급한다.
 * 파일 증거는 제출 시 저장 스트림에서 확정한 SHA-256을 그대로 사용한다.
 */
@Component
@RequiredArgsConstructor
public class AwardCredentialIssuer {

    private final CredentialIssuanceService credentialIssuanceService;
    private final TeamMemberRepository teamMemberRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final ObjectMapper objectMapper;

    public IssuedCredential issueForConfirmedAward(Award award) {
        Team team = award.getTeam();
        Submission submission = award.getContestStageEntry().getSubmission();
        Instant confirmedAt = toInstant(award);

        return credentialIssuanceService.issue(new CredentialIssueCommand(
                team.getContest().getOrganization().getId(),
                award.getCertificateNo(),
                CredentialType.AWARD,
                CredentialSchemaProfiles.AWARD_V1,
                source(award, confirmedAt),
                subjects(award, team),
                files(submission),
                confirmedAt,
                null));
    }

    //======= 헬퍼 메서드 ==========

    private CredentialIssueCommand.Source source(Award award, Instant confirmedAt) {
        //발급 시점 사실을 snapshot 원문으로 고정한다 — 이후 업무 데이터가 바뀌어도 Credential은 불변
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("contestTitle", award.getTeam().getContest().getTitle());
        snapshot.put("prize", award.getPrize());
        snapshot.put("awardRankNo", award.getAwardRankNo());
        snapshot.put("certificateNo", award.getCertificateNo());
        snapshot.put("submissionTitle", award.getContestStageEntry().getSubmission().getTitle());
        if (award.getContestStageEntry().getFinalScore() != null) {
            snapshot.put("finalScore", award.getContestStageEntry().getFinalScore().toPlainString());
        }

        return new CredentialIssueCommand.Source(
                CredentialSourceType.AWARD,
                null,
                null,
                award.getId(),
                award.getPublicId(),
                confirmedAt,
                snapshot);
    }

    // 수상 팀 + 발급 당시 구성원 snapshot (erd-mvp §5 — 개인 이력 조회의 근거)
    private List<CredentialIssueCommand.Subject> subjects(Award award, Team team) {
        List<CredentialIssueCommand.Subject> subjects = new ArrayList<>();
        subjects.add(new CredentialIssueCommand.Subject(
                null,
                team.getId(),
                "team:" + team.getPublicId(),
                CredentialSubjectType.TEAM,
                team.getName(),
                team.getMajor(),
                "TEAM",
                DisclosureClass.PUBLIC,
                0));
        subjects.add(new CredentialIssueCommand.Subject(
                team.getLeaderUser().getId(),
                null,
                "user:" + team.getLeaderUser().getId(),
                CredentialSubjectType.USER,
                team.getLeaderName(),
                team.getMajor(),
                "REPRESENTATIVE",
                DisclosureClass.PUBLIC,
                1));

        int order = 2;
        for (TeamMember member : teamMemberRepository.findAllByTeamId(team.getId())) {
            //리더는 위에서 이미 추가됨
            if (member.getUser().getId().equals(team.getLeaderUser().getId())) {
                continue;
            }
            subjects.add(new CredentialIssueCommand.Subject(
                    member.getUser().getId(),
                    null,
                    "user:" + member.getUser().getId(),
                    CredentialSubjectType.USER,
                    member.getUser().getName(),
                    member.getUser().getMajor(),
                    "AWARDEE",
                    DisclosureClass.PUBLIC,
                    order++));
        }
        return subjects;
    }

    private List<CredentialIssueCommand.FileEvidence> files(Submission submission) {
        return submissionFileRepository.findAllBySubmissionId(submission.getId()).stream()
                .map(file -> new CredentialIssueCommand.FileEvidence(
                        file.getOriginalName(),
                        file.getContentType(),
                        file.getSizeBytes(),
                        file.getSha256()))
                .toList();
    }

    private Instant toInstant(Award award) {
        return award.getConfirmedAt().atZone(ZoneId.systemDefault()).toInstant();
    }
}
