package com.api.trekkey.domain.credential.integration;

import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.service.CredentialIssuanceService;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.IssuedCredential;
import com.api.trekkey.domain.credential.service.support.UtcTime;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.team.entity.Team;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 업무 원장 → Credential 발급 연결 (erd-mvp §6).
 * 작품 Credential — 원천은 첫 심사 시작으로 확정된 SUBMISSION,
 * 파일 증거는 저장 스트림에서 확정한 SHA-256을 그대로 사용한다.
 */
@Component
@RequiredArgsConstructor
public class WorkCredentialIssuer {

    private final CredentialIssuanceService credentialIssuanceService;
    private final CredentialSubjectAssembler subjectAssembler;
    private final ObjectMapper objectMapper;

    public IssuedCredential issueForFinalizedSubmission(Submission submission) {
        Team team = submission.getTeam();
        Instant finalizedAt = UtcTime.toInstant(submission.getFinalizedAt());

        //발급 시점 사실을 snapshot 원문으로 고정 — 이후 업무 데이터가 바뀌어도 Credential은 불변
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("contestTitle", team.getContest().getTitle());
        snapshot.put("teamName", team.getName());
        snapshot.put("submissionTitle", submission.getTitle());

        return credentialIssuanceService.issue(new CredentialIssueCommand(
                team.getContest().getOrganization().getId(),
                workNo(submission),
                CredentialType.WORK,
                CredentialSchemaProfiles.WORK_V1,
                new CredentialIssueCommand.Source(
                        CredentialSourceType.SUBMISSION,
                        null,
                        submission.getId(),
                        null,
                        submission.getPublicId(),
                        finalizedAt,
                        snapshot),
                subjectAssembler.teamSubjects(team, "PARTICIPANT"),
                subjectAssembler.fileEvidences(submission.getId()),
                finalizedAt,
                null));
    }

    // 작품확인 번호 — 연도-W대회-T팀 (조직 내 유일, AWARD의 상장번호 체계와 동형)
    private String workNo(Submission submission) {
        return String.format("%d-W%d-T%d",
                submission.getFinalizedAt().getYear(),
                submission.getTeam().getContest().getId(),
                submission.getTeam().getId());
    }
}
