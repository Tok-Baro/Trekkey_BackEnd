package com.api.trekkey.domain.credential.integration;

import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.service.CredentialIssuanceService;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.IssuedCredential;
import com.api.trekkey.domain.team.entity.Team;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 업무 원장 → Credential 발급 연결 (erd-mvp §6).
 * 참여 Credential — 원천은 명단이 확정된 TEAM, 주체는 팀과 확정 팀원 전원.
 */
@Component
@RequiredArgsConstructor
public class ParticipationCredentialIssuer {

    private final CredentialIssuanceService credentialIssuanceService;
    private final CredentialSubjectAssembler subjectAssembler;
    private final ObjectMapper objectMapper;

    public IssuedCredential issueForFinalizedTeam(Team team) {
        Instant finalizedAt = team.getParticipationFinalizedAt()
                .atZone(ZoneId.systemDefault()).toInstant();

        //발급 시점 사실을 snapshot 원문으로 고정 — 이후 업무 데이터가 바뀌어도 Credential은 불변
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("contestTitle", team.getContest().getTitle());
        snapshot.put("teamName", team.getName());
        snapshot.put("memberCount", team.getMemberCount());

        return credentialIssuanceService.issue(new CredentialIssueCommand(
                team.getContest().getOrganization().getId(),
                participationNo(team),
                CredentialType.PARTICIPATION,
                CredentialSchemaProfiles.PARTICIPATION_V1,
                new CredentialIssueCommand.Source(
                        CredentialSourceType.TEAM,
                        team.getId(),
                        null,
                        null,
                        team.getPublicId(),
                        finalizedAt,
                        snapshot),
                subjectAssembler.teamSubjects(team, "PARTICIPANT"),
                List.of(),
                finalizedAt,
                null));
    }

    // 참여확인 번호 — 연도-P대회-T팀 (조직 내 유일, AWARD의 상장번호 체계와 동형)
    private String participationNo(Team team) {
        return String.format("%d-P%d-T%d",
                team.getParticipationFinalizedAt().getYear(),
                team.getContest().getId(),
                team.getId());
    }
}
