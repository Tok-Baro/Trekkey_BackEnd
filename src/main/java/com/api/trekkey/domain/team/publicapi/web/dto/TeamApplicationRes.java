package com.api.trekkey.domain.team.publicapi.web.dto;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import java.time.LocalDateTime;

public record TeamApplicationRes(
        String contestPublicId,
        String contestTitle,
        String department,
        ParticipationType participationType,
        int maxTeamMembers,
        String teamName,
        String leaderName,
        String major,
        int memberCount,
        TeamStatus status,
        String contactEmail,
        String phone,
        String motivation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TeamApplicationRes from(Team team) {
        Contest contest = team.getContest();

        return new TeamApplicationRes(
                contest.getPublicId(),
                contest.getTitle(),
                contest.getDepartment(),
                contest.getParticipationType(),
                contest.getMaxTeamMembers(),
                team.getName(),
                team.getLeaderName(),
                team.getMajor(),
                team.getMemberCount(),
                team.getStatus(),
                team.getContactEmail(),
                team.getPhone(),
                team.getMotivation(),
                team.getCreatedAt(),
                team.getUpdatedAt());
    }
}
