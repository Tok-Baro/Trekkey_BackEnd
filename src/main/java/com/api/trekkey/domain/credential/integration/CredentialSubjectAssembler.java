package com.api.trekkey.domain.credential.integration;

import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Credential 발급 명령의 주체·파일 증거 조립 (erd-mvp §5·§6).
 * 팀 + 대표 + 발급 당시 구성원 전원을 subject snapshot으로 고정한다 —
 * 개인 이력 조회는 현재 팀이 아니라 이 snapshot을 근거로 한다.
 */
@Component
@RequiredArgsConstructor
public class CredentialSubjectAssembler {

    private final TeamMemberRepository teamMemberRepository;
    private final SubmissionFileRepository submissionFileRepository;

    // memberRoleCode: 참여·작품은 PARTICIPANT, 수상은 AWARDEE (erd-mvp SUBJECT.roleCode 명세)
    public List<CredentialIssueCommand.Subject> teamSubjects(Team team, String memberRoleCode) {
        // user:<PK>는 내부 fingerprint 입력일 뿐이며 issuance service가 공개 payload 전에 무작위 ref로 교체한다.
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
        for (TeamMember member : teamMemberRepository.findAllByTeamIdOrderByUserIdAsc(team.getId())) {
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
                    memberRoleCode,
                    DisclosureClass.PUBLIC,
                    order++));
        }
        return subjects;
    }

    public List<CredentialIssueCommand.FileEvidence> fileEvidences(Long submissionId) {
        return submissionFileRepository.findAllBySubmissionId(submissionId).stream()
                .map(file -> new CredentialIssueCommand.FileEvidence(
                        file.getOriginalName(),
                        file.getContentType(),
                        file.getSizeBytes(),
                        //제출 도메인은 접두사 없는 hex로 저장, credential 암호화 계층은 0x 접두사를 요구
                        "0x" + file.getSha256()))
                .toList();
    }
}
