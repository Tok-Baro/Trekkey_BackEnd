package com.api.trekkey.domain.team.publicapi.web.dto;

import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import com.api.trekkey.domain.team.entity.TeamStatus;
import java.time.LocalDateTime;

public record ParticipantTeamRes(
        String teamPublicId,
        String contestPublicId,
        String contestTitle,
        String teamName,
        TeamMemberRole myRole,
        int memberCount,
        TeamStatus status,
        LocalDateTime participationFinalizedAt
) {
    public static ParticipantTeamRes from(TeamMember teamMember) {
        Team team = teamMember.getTeam();

        return new ParticipantTeamRes(
                team.getPublicId(),
                team.getContest().getPublicId(),
                team.getContest().getTitle(),
                team.getName(),
                teamMember.getRole(),
                team.getMemberCount(),
                team.getStatus(),
                team.getParticipationFinalizedAt());
    }
}
