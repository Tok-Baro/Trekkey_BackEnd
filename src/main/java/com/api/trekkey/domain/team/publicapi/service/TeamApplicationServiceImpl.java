package com.api.trekkey.domain.team.publicapi.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantSearchRes;
import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantTeamRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamApplicationServiceImpl implements TeamApplicationService {

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    @Transactional
    public void createApplication(Long userId, String contestPublicId, TeamApplicationCreateReq request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));

        /*
            publicId와 학교Id로 검색을 하여 존재하지 않는다면 예외처리
            이는 곧 사용자 자신의 학교에 존재하는 대회를 검색하는 의미이다.
         */
        Contest contest = contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                        contestPublicId,
                        user.getOrganization().getId(),
                        Set.of(
                                ContestStatus.APPLICATION_OPEN,
                                ContestStatus.REVIEWING,
                                ContestStatus.AWARDED))
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));

        //대회 검색은 성공했지만 대회가 OPEN인 상태가 아니라면 예외처리
        if (contest.getStatus() != ContestStatus.APPLICATION_OPEN) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_NOT_OPEN);
        }
        validateMemberCount(contest, request.memberUserIds());
        //이미 대회id에 같은 대표자id가 있다면 예외처리
        if (teamRepository.existsByContestIdAndLeaderUserId(contest.getId(), user.getId())) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_ALREADY_EXISTS);
        }

        List<User> members = getValidMembers(user, request.memberUserIds());
        List<Long> participantUserIds = new ArrayList<>(request.memberUserIds());
        participantUserIds.add(user.getId());
        if (teamMemberRepository.existsByTeamContestIdAndUserIdIn(
                contest.getId(), participantUserIds)) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING);
        }

        Team team = teamRepository.save(Team.builder()
                .contest(contest)
                .leaderUser(user)
                .name(request.teamName().trim())
                .leaderName(request.leaderName().trim())
                .major(request.major().trim())
                .memberCount(members.size() + 1)
                .status(TeamStatus.PENDING)
                .contactEmail(request.contactEmail().trim())
                .phone(request.phone().trim())
                .motivation(request.motivation().trim())
                .build());

        List<TeamMember> teamMembers = new ArrayList<>();
        TeamMember leader = TeamMember.builder()
                .team(team)
                .user(user)
                .role(TeamMemberRole.LEADER)
                .build();
        teamMembers.add(leader);

        for (User member : members) {
            TeamMember teamMember = TeamMember.builder()
                    .team(team)
                    .user(member)
                    .role(TeamMemberRole.MEMBER)
                    .build();
            teamMembers.add(teamMember);
        }

        teamMemberRepository.saveAll(teamMembers);
    }

    @Override
    public List<TeamApplicationRes> getMyApplications(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));

        return teamMemberRepository.findAllWithTeamAndContestByUserId(userId).stream()
                .map(TeamMember::getTeam)
                .map(TeamApplicationRes::from)
                .toList();
    }

    @Override
    public List<ParticipantSearchRes> searchParticipants(Long userId, String keyword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));

        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        return userRepository.searchParticipants(
                        user.getOrganization().getId(),
                        UserRole.PARTICIPANT,
                        UserStatus.ACTIVE,
                        userId,
                        keyword.trim(),
                        PageRequest.of(0, 20)).stream()
                .map(ParticipantSearchRes::from)
                .toList();
    }

    @Override
    public List<ParticipantTeamRes> getMyTeams(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));

        return teamMemberRepository.findAllWithTeamAndContestByUserId(userId).stream()
                .map(ParticipantTeamRes::from)
                .toList();
    }

    @Override
    @Transactional
    public void updateApplication(Long userId, String contestPublicId, TeamApplicationUpdateReq request) {
        Team team = teamRepository.findByContestPublicIdAndLeaderUserId(contestPublicId, userId)
                .orElseThrow(() -> new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND));

        if (team.isFinalized()) {
            throw new CustomException(TeamErrorResponseCode.TEAM_ALREADY_FINALIZED);
        }

        validateMemberCount(team.getContest(), request.memberUserIds());

        User leader = team.getLeaderUser();
        List<User> members = getValidMembers(leader, request.memberUserIds());
        List<Long> participantUserIds = new ArrayList<>(request.memberUserIds());
        participantUserIds.add(leader.getId());
        if (teamMemberRepository.existsByContestIdAndUserIdInAndTeamIdNot(
                team.getContest().getId(), participantUserIds, team.getId())) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING);
        }

        synchronizeMembers(team, leader, members);

        team.updateApplication(
                request.teamName().trim(),
                request.leaderName().trim(),
                request.major().trim(),
                members.size() + 1,
                request.contactEmail().trim(),
                request.phone().trim(),
                request.motivation().trim());
    }

    private void validateMemberCount(Contest contest, List<Long> memberUserIds) {
        if (memberUserIds.size() > 4
                || (contest.getParticipationType() == ParticipationType.INDIVIDUAL
                        && !memberUserIds.isEmpty())) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);
        }
    }

    private List<User> getValidMembers(User leader, List<Long> memberUserIds) {
        if (memberUserIds.stream().anyMatch(id -> id == null || id <= 0)
                || memberUserIds.contains(leader.getId())
                || new HashSet<>(memberUserIds).size() != memberUserIds.size()) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_INVALID);
        }
        if (memberUserIds.isEmpty()) {
            return List.of();
        }

        List<User> members = userRepository.findAllByIdInAndOrganizationIdAndRoleAndStatus(
                memberUserIds,
                leader.getOrganization().getId(),
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE);
        if (members.size() != memberUserIds.size()) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_INVALID);
        }
        return members;
    }

    private void synchronizeMembers(Team team, User leader, List<User> requestedMembers) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(team.getId(), leader.getId())) {
            teamMemberRepository.save(TeamMember.builder()
                    .team(team)
                    .user(leader)
                    .role(TeamMemberRole.LEADER)
                    .build());
        }

        List<TeamMember> currentMembers =
                teamMemberRepository.findAllByTeamIdAndRole(team.getId(), TeamMemberRole.MEMBER);
        Set<Long> requestedUserIds = new HashSet<>();
        for (User requestedMember : requestedMembers) {
            requestedUserIds.add(requestedMember.getId());
        }

        Set<Long> currentUserIds = new HashSet<>();
        List<TeamMember> membersToDelete = new ArrayList<>();
        for (TeamMember currentMember : currentMembers) {
            Long currentUserId = currentMember.getUser().getId();
            currentUserIds.add(currentUserId);
            if (!requestedUserIds.contains(currentUserId)) {
                membersToDelete.add(currentMember);
            }
        }
        teamMemberRepository.deleteAll(membersToDelete);

        List<TeamMember> membersToAdd = new ArrayList<>();
        for (User requestedMember : requestedMembers) {
            if (!currentUserIds.contains(requestedMember.getId())) {
                membersToAdd.add(TeamMember.builder()
                        .team(team)
                        .user(requestedMember)
                        .role(TeamMemberRole.MEMBER)
                        .build());
            }
        }
        teamMemberRepository.saveAll(membersToAdd);
    }
}
