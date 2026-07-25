package com.api.trekkey.domain.team.publicapi.web.dto;

import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import java.time.LocalDateTime;

public record TeamRes(
        // 공개 식별자 — 내부 PK는 노출하지 않는다
        String id,
        String contestId,
        String contestTitle,
        ContestStatus contestStatus,
        String name,
        String leaderName,
        String major,
        int memberCount,
        TeamStatus status,
        String contactEmail,
        String phone,
        String motivation,
        LocalDateTime participationFinalizedAt,
        LocalDateTime createdAt
) {
    public static TeamRes from(Team team) {
        return new TeamRes(
                team.getPublicId(),
                team.getContest().getPublicId(),
                team.getContest().getTitle(),
                team.getContest().getStatus(),
                team.getName(),
                team.getLeaderName(),
                team.getMajor(),
                team.getMemberCount(),
                team.getStatus(),
                team.getContactEmail(),
                team.getPhone(),
                team.getMotivation(),
                team.getParticipationFinalizedAt(),
                team.getCreatedAt()
        );
    }
}
