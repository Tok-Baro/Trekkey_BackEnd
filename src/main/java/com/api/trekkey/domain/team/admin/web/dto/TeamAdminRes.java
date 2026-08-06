package com.api.trekkey.domain.team.admin.web.dto;

import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.web.dto.TeamMemberSummaryRes;
import java.time.LocalDateTime;
import java.util.List;

public record TeamAdminRes(
        // 공개 식별자 — 내부 PK는 노출하지 않는다
        String id,
        String contestId,
        String contestTitle,
        ContestStatus contestStatus,
        String name,
        String leaderName,
        String major,
        int memberCount,
        List<TeamMemberSummaryRes> members,
        TeamStatus status,
        String revisionReason,
        String contactEmail,
        String phone,
        String motivation,
        LocalDateTime participationFinalizedAt,
        LocalDateTime createdAt
) {
    public static TeamAdminRes from(
            Team team,
            List<TeamMemberSummaryRes> members) {
        return new TeamAdminRes(
                team.getPublicId(),
                team.getContest().getPublicId(),
                team.getContest().getTitle(),
                team.getContest().getStatus(),
                team.getName(),
                team.getLeaderName(),
                team.getMajor(),
                team.getMemberCount(),
                members,
                team.getStatus(),
                team.getRevisionReason(),
                team.getContactEmail(),
                team.getPhone(),
                team.getMotivation(),
                team.getParticipationFinalizedAt(),
                team.getCreatedAt()
        );
    }
}
