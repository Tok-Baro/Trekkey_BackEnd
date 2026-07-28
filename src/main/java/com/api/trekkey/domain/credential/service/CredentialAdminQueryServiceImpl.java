package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialSourceRepository;
import com.api.trekkey.domain.credential.web.dto.CredentialSummaryRes;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 발급 현황 조회 (erd-mvp §13) — 대회·팀 단위로 발급된 Credential 전체.
 * 타 조직 자원은 존재 자체를 404로 비노출한다 (organization scope).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialAdminQueryServiceImpl implements CredentialAdminQueryService {

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final TeamRepository teamRepository;
    private final AncCredentialSourceRepository credentialSourceRepository;

    @Override
    public List<CredentialSummaryRes> getContestCredentials(Long adminUserId, String contestPublicId) {
        User admin = findAdmin(adminUserId);
        Contest contest = contestRepository.findByPublicId(contestPublicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        //타 조직 대회는 존재 자체를 비노출한다 (erd-mvp §13 organization scope)
        if (!contest.getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND);
        }

        return credentialSourceRepository.findSummaryRowsByContestId(contest.getId()).stream()
                .map(CredentialSummaryRes::from)
                .toList();
    }

    @Override
    public List<CredentialSummaryRes> getTeamCredentials(Long adminUserId, String teamPublicId) {
        User admin = findAdmin(adminUserId);
        Team team = teamRepository.findByPublicId(teamPublicId)
                .orElseThrow(() -> new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND));
        if (!team.getContest().getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND);
        }

        return credentialSourceRepository.findSummaryRowsByTeamId(team.getId()).stream()
                .map(CredentialSummaryRes::from)
                .toList();
    }

    //======= 헬퍼 메서드 ==========

    private User findAdmin(Long adminUserId) {
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }
}
