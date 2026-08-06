package com.api.trekkey.domain.team.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.credential.integration.ParticipationCredentialIssuer;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminListRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamStatusUpdateReq;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.team.web.dto.TeamMemberSummaryRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final TeamMemberRepository teamMemberRepository;
    private final ParticipationCredentialIssuer participationCredentialIssuer;
    private final Clock clock;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    public TeamAdminListRes getTeams(Long adminUserId, String contestPublicId, TeamStatus status) {
        User admin = findAdmin(adminUserId);

        Contest contest = contestRepository.findByPublicId(contestPublicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        //타 조직 대회는 존재 자체를 비노출한다 — 403이 아니라 404 (보안 방침 일관성)
        if (!contest.getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND);
        }

        // 상태별 카운트는 필터와 무관하게 전체 기준으로 제공한다 (관리자 화면 요약)
        List<Team> allTeams = teamRepository.findAllByContestIdOrderByCreatedAtDesc(contest.getId());
        Map<TeamStatus, Long> statusCounts = allTeams.stream()
                .collect(Collectors.groupingBy(Team::getStatus, Collectors.counting()));

        List<Team> visibleTeams = status == null
                ? allTeams
                : allTeams.stream().filter(team -> team.getStatus() == status).toList();
        Map<Long, List<TeamMemberSummaryRes>> membersByTeamId =
                getMembersByTeamId(visibleTeams);

        return new TeamAdminListRes(
                visibleTeams.stream()
                        .map(team -> TeamAdminRes.from(
                                team,
                                membersByTeamId.getOrDefault(
                                        team.getId(),
                                        List.of())))
                        .toList(),
                statusCounts,
                allTeams.size());
    }

    @Override
    @Transactional
    public TeamAdminRes changeStatus(Long adminUserId, String teamPublicId, TeamStatusUpdateReq request) {
        User admin = findAdmin(adminUserId);
        Team team = findTeamInAdminOrganization(teamPublicId, admin);

        TeamStatus previousStatus = team.getStatus();
        if (team.isFinalized()
                && previousStatus != request.status()) {
            throw new CustomException(
                    TeamErrorResponseCode.TEAM_ALREADY_FINALIZED);
        }
        String revisionReason = normalizeRevisionReason(request);
        team.changeStatus(request.status(), revisionReason);

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.TEAM_STATUS_CHANGE,
                TARGET_TYPE_TEAM, team.getId(), "status: " + previousStatus + "→" + request.status());

        return TeamAdminRes.from(team, getMembers(team));
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

        //Credential 원문에 들어가는 확정 시각은 UTC 기준으로 고정한다 (award 확정과 동일 규약)
        team.finalizeParticipation(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        //명단 확정과 참여 Credential 발급을 한 트랜잭션으로 (erd-mvp §6 — 원천: 확정 TEAM)
        participationCredentialIssuer.issueForFinalizedTeam(team);

        adminAuditLogger.log(admin.getId(), admin.getOrganization().getId(), AuditAction.TEAM_FINALIZE,
                TARGET_TYPE_TEAM, team.getId(), team.getName());

        return TeamAdminRes.from(team, getMembers(team));
    }

    //======= 헬퍼 메서드 ==========

    private User findAdmin(Long adminUserId) {
        User user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(
                    UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
    }

    private String normalizeRevisionReason(TeamStatusUpdateReq request) {
        if (request.status() != TeamStatus.REVISION_REQUESTED) {
            return null;
        }
        if (request.revisionReason() == null
                || request.revisionReason().isBlank()) {
            throw new CustomException(
                    TeamErrorResponseCode.TEAM_REVISION_REASON_REQUIRED);
        }
        return request.revisionReason().trim();
    }

    private List<TeamMemberSummaryRes> getMembers(Team team) {
        return teamMemberRepository
                .findAllByTeamIdOrderByUserIdAsc(team.getId())
                .stream()
                .map(TeamMemberSummaryRes::from)
                .toList();
    }

    private Map<Long, List<TeamMemberSummaryRes>> getMembersByTeamId(
            List<Team> teams) {
        if (teams.isEmpty()) {
            return Map.of();
        }

        return teamMemberRepository
                .findAllByTeamIdInOrderByTeamIdAscUserIdAsc(
                        teams.stream().map(Team::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        member -> member.getTeam().getId(),
                        Collectors.mapping(
                                TeamMemberSummaryRes::from,
                                Collectors.toList())));
    }

    // 팀 조회 + 관리자 소속 조직 검증 — 타 학교 신청은 존재 여부를 노출하지 않고 404로 응답한다
    private Team findTeamInAdminOrganization(String teamPublicId, User admin) {
        Team team = teamRepository.findByPublicIdForUpdate(teamPublicId)
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
