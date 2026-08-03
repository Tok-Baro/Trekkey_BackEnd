package com.api.trekkey.domain.team.publicapi.service;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes.Step;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes.StepStatus;
import com.api.trekkey.domain.team.publicapi.web.dto.ApplicationProgressRes.StepType;
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
import java.util.Optional;
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
    private final SubmissionRepository submissionRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewRoundEntryRepository reviewRoundEntryRepository;
    private final AwardRepository awardRepository;

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
    public ApplicationProgressRes getApplicationProgress(Long userId, String contestPublicId) {
        Team team = teamMemberRepository
                .findWithTeamAndContestByUserIdAndContestPublicId(userId, contestPublicId)
                .map(TeamMember::getTeam)
                .orElseThrow(() -> new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND));

        List<ReviewRound> reviewRounds =
                reviewRoundRepository.findAllByContestIdOrderByRoundNoAsc(
                        team.getContest().getId());
        Optional<Submission> submission = submissionRepository.findByTeamId(team.getId());
        List<ReviewRoundEntry> entries = submission
                .map(found -> reviewRoundEntryRepository
                        .findAllWithRoundBySubmissionIdOrderByRoundNoAsc(
                                found.getId()))
                .orElseGet(List::of);
        Optional<Award> award = team.getContest().getStatus() == ContestStatus.AWARDED
                ? awardRepository.findFirstByTeamIdAndStatusOrderByAwardRankNoAsc(
                        team.getId(), AwardStatus.CONFIRMED)
                : Optional.empty();

        return new ApplicationProgressRes(
                team.getContest().getPublicId(),
                List.of(
                        applicationReceivedStep(team),
                        applicationReviewStep(team),
                        submissionStep(submission),
                        reviewStep(entries, reviewRounds),
                        resultStep(team.getContest().getStatus(), award)));
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
        Team team = teamRepository
                .findByContestPublicIdAndLeaderUserIdForUpdate(
                        contestPublicId,
                        userId)
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

        int memberCount = addMissingMembers(team, leader, members);

        team.updateApplication(
                request.teamName().trim(),
                request.leaderName().trim(),
                request.major().trim(),
                memberCount,
                request.contactEmail().trim(),
                request.phone().trim(),
                request.motivation().trim());
    }

    private Step applicationReceivedStep(Team team) {
        return Step.of(
                StepType.APPLICATION_RECEIVED,
                StepStatus.COMPLETED,
                "접수 완료",
                team.getCreatedAt());
    }

    private Step applicationReviewStep(Team team) {
        return switch (team.getStatus()) {
            case PENDING -> Step.of(
                    StepType.APPLICATION_REVIEW,
                    StepStatus.IN_PROGRESS,
                    "검토 중",
                    null);
            case APPROVED -> Step.of(
                    StepType.APPLICATION_REVIEW,
                    StepStatus.COMPLETED,
                    "승인",
                    null);
            case REVISION_REQUESTED -> Step.of(
                    StepType.APPLICATION_REVIEW,
                    StepStatus.IN_PROGRESS,
                    "보완 요청",
                    null);
            case REJECTED -> Step.of(
                    StepType.APPLICATION_REVIEW,
                    StepStatus.FAILED,
                    "반려",
                    null);
        };
    }

    private Step submissionStep(Optional<Submission> submission) {
        if (submission.isEmpty()) {
            return Step.of(
                    StepType.SUBMISSION,
                    StepStatus.WAITING,
                    "제출 전",
                    null);
        }

        Submission found = submission.get();
        return switch (found.getStatus()) {
            case DRAFT -> Step.of(
                    StepType.SUBMISSION,
                    StepStatus.IN_PROGRESS,
                    "작성 중",
                    found.getSubmittedAt());
            case SUBMITTED -> Step.of(
                    StepType.SUBMISSION,
                    StepStatus.COMPLETED,
                    "제출 완료",
                    found.getSubmittedAt());
            case WITHDRAWN -> Step.of(
                    StepType.SUBMISSION,
                    StepStatus.FAILED,
                    "제출 철회",
                    found.getSubmittedAt());
        };
    }

    private Step reviewStep(
            List<ReviewRoundEntry> entries,
            List<ReviewRound> reviewRounds) {
        if (entries.isEmpty()) {
            return Step.of(
                    StepType.REVIEW,
                    StepStatus.WAITING,
                    "심사 대기",
                    null);
        }

        ReviewRoundEntry latestEntry = entries.get(entries.size() - 1);
        String roundName = latestEntry.getReviewRound().getName();
        return switch (latestEntry.getStatus()) {
            case ELIGIBLE -> Step.of(
                    StepType.REVIEW,
                    StepStatus.IN_PROGRESS,
                    roundName + " 판정 대기",
                    null);
            case IN_REVIEW -> Step.of(
                    StepType.REVIEW,
                    StepStatus.IN_PROGRESS,
                    roundName + " 진행 중",
                    null);
            case SELECTED -> selectedReviewStep(
                    latestEntry,
                    reviewRounds);
            case NOT_SELECTED -> Step.of(
                    StepType.REVIEW,
                    StepStatus.FAILED,
                    roundName + " 탈락",
                    latestEntry.getFinalizedAt());
            case WITHDRAWN -> Step.of(
                    StepType.REVIEW,
                    StepStatus.FAILED,
                    roundName + " 철회",
                    latestEntry.getFinalizedAt());
            case DISQUALIFIED -> Step.of(
                    StepType.REVIEW,
                    StepStatus.FAILED,
                    roundName + " 실격",
                    latestEntry.getFinalizedAt());
        };
    }

    private Step selectedReviewStep(
            ReviewRoundEntry latestEntry,
            List<ReviewRound> reviewRounds) {
        return reviewRounds.stream()
                .filter(round -> round.getRoundNo()
                        > latestEntry.getReviewRound().getRoundNo())
                .findFirst()
                .map(nextRound -> Step.of(
                        StepType.REVIEW,
                        StepStatus.IN_PROGRESS,
                        nextRound.getName() + " 대기",
                        null))
                .orElseGet(() -> Step.of(
                        StepType.REVIEW,
                        StepStatus.COMPLETED,
                        latestEntry.getReviewRound().getName() + " 통과",
                        latestEntry.getFinalizedAt()));
    }

    private Step resultStep(ContestStatus contestStatus, Optional<Award> award) {
        if (contestStatus != ContestStatus.AWARDED) {
            return Step.of(
                    StepType.RESULT,
                    StepStatus.WAITING,
                    "발표 전",
                    null);
        }

        return award
                .map(found -> Step.of(
                        StepType.RESULT,
                        StepStatus.COMPLETED,
                        found.getPrize(),
                        found.getConfirmedAt()))
                .orElseGet(() -> Step.of(
                        StepType.RESULT,
                        StepStatus.COMPLETED,
                        "수상 내역 없음",
                        null));
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

    private int addMissingMembers(Team team, User leader, List<User> requestedMembers) {
        boolean leaderExists =
                teamMemberRepository.existsByTeamIdAndUserId(team.getId(), leader.getId());
        List<TeamMember> currentMembers =
                teamMemberRepository.findAllByTeamIdAndRole(team.getId(), TeamMemberRole.MEMBER);
        Set<Long> finalMemberUserIds = new HashSet<>();
        currentMembers.forEach(member -> finalMemberUserIds.add(member.getUser().getId()));
        requestedMembers.forEach(member -> finalMemberUserIds.add(member.getId()));
        if (finalMemberUserIds.size() > 4) {
            throw new CustomException(
                    TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);
        }

        List<TeamMember> membersToAdd = new ArrayList<>();
        for (User requestedMember : requestedMembers) {
            boolean alreadyExists = currentMembers.stream()
                    .anyMatch(member ->
                            member.getUser().getId().equals(requestedMember.getId()));
            if (!alreadyExists) {
                membersToAdd.add(TeamMember.builder()
                        .team(team)
                        .user(requestedMember)
                        .role(TeamMemberRole.MEMBER)
                        .build());
            }
        }

        if (!leaderExists) {
            teamMemberRepository.save(TeamMember.builder()
                    .team(team)
                    .user(leader)
                    .role(TeamMemberRole.LEADER)
                    .build());
        }
        teamMemberRepository.saveAll(membersToAdd);
        return finalMemberUserIds.size() + 1;
    }
}
