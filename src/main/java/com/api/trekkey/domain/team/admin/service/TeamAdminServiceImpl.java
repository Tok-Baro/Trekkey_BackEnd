package com.api.trekkey.domain.team.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminListRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamStatusUpdateReq;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamAdminServiceImpl implements TeamAdminService {

    private static final String TARGET_TYPE_TEAM = "TEAM";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final TeamRepository teamRepository;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    public TeamAdminListRes getTeams(Long adminUserId, String contestPublicId, TeamStatus status) {
        User admin = findAdmin(adminUserId);

        Contest contest = contestRepository.findByPublicId(contestPublicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        validateSameOrganization(contest, admin);

        // 상태별 카운트는 필터와 무관하게 전체 기준으로 제공한다 (관리자 화면 요약)
        List<Team> allTeams = teamRepository.findAllByContestIdOrderByCreatedAtDesc(contest.getId());
        Map<TeamStatus, Long> statusCounts = allTeams.stream()
                .collect(Collectors.groupingBy(Team::getStatus, Collectors.counting()));

        List<Team> visibleTeams = status == null
                ? allTeams
                : allTeams.stream().filter(team -> team.getStatus() == status).toList();

        return new TeamAdminListRes(
                visibleTeams.stream().map(TeamAdminRes::from).toList(),
                statusCounts,
                allTeams.size());
    }

    @Override
    @Transactional
    public TeamAdminRes changeStatus(Long adminUserId, String teamPublicId, TeamStatusUpdateReq request) {
        User admin = findAdmin(adminUserId);
        Team team = findTeamInAdminOrganization(teamPublicId, admin);

        TeamStatus previousStatus = team.getStatus();
        team.changeStatus(request.status());

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.TEAM_STATUS_CHANGE,
                TARGET_TYPE_TEAM, team.getId(), "status: " + previousStatus + "→" + request.status());

        return TeamAdminRes.from(team);
    }

    @Override
    @Transactional
    public TeamAdminRes finalizeParticipation(Long adminUserId, String teamPublicId) {
        User admin = findAdmin(adminUserId);
        Team team = findTeamInAdminOrganization(teamPublicId, admin);

        if (team.isFinalized()) {
            throw new CustomException(TeamErrorResponseCode.TEAM_ALREADY_FINALIZED);
        }
        //승인된 팀만 명단을 확정할 수 있다
        if (team.getStatus() != TeamStatus.APPROVED) {
            throw new CustomException(TeamErrorResponseCode.TEAM_NOT_APPROVED);
        }

        team.finalizeParticipation(LocalDateTime.now());

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.TEAM_FINALIZE,
                TARGET_TYPE_TEAM, team.getId(), team.getName());

        return TeamAdminRes.from(team);
    }

    //======= 헬퍼 메서드 ==========

    private User findAdmin(Long adminUserId) {
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }

    // 팀 조회 + 관리자 소속 조직 검증 — 타 학교 신청은 존재 여부를 노출하지 않고 404로 응답한다
    private Team findTeamInAdminOrganization(String teamPublicId, User admin) {
        Team team = teamRepository.findByPublicId(teamPublicId)
                .orElseThrow(() -> new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND));

        if (!team.getContest().getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND);
        }
        return team;
    }

    private void validateSameOrganization(Contest contest, User admin) {
        if (!contest.getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
    }
}
