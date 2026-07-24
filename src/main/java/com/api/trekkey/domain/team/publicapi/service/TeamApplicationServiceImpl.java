package com.api.trekkey.domain.team.publicapi.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamApplicationServiceImpl implements TeamApplicationService {

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final TeamRepository teamRepository;

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
        //대회가 개인전이지만 참가자 수가 1명이 아니라면 예외처리
        if (contest.getParticipationType() == ParticipationType.INDIVIDUAL
                && request.memberCount() != 1) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_MEMBER_COUNT_INVALID);
        }
        //이미 대회id에 같은 대표자id가 있다면 예외처리
        if (teamRepository.existsByContestIdAndLeaderUserId(contest.getId(), user.getId())) {
            throw new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_ALREADY_EXISTS);
        }

        teamRepository.save(Team.builder()
                .contest(contest)
                .leaderUser(user)
                .name(request.teamName().trim())
                .leaderName(request.leaderName().trim())
                .major(request.major().trim())
                .memberCount(request.memberCount())
                .status(TeamStatus.PENDING)
                .contactEmail(request.contactEmail().trim())
                .phone(request.phone().trim())
                .motivation(request.motivation().trim())
                .build());
    }
}
