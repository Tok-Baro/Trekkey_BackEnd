package com.api.trekkey.domain.team.publicapi.web.dto;

import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.web.dto.TeamMemberSummaryRes;
import java.time.LocalDateTime;
import java.util.List;

public record ParticipantTeamRes(
        String teamPublicId,
        String contestPublicId,
        String contestTitle,
        String teamName,
        TeamMemberRole myRole,
        int memberCount,
        List<TeamMemberSummaryRes> members,
        TeamStatus status,
        String revisionReason,
        LocalDateTime participationFinalizedAt
) {
    public static ParticipantTeamRes from(
            TeamMember teamMember,
            List<TeamMemberSummaryRes> members) {
        Team team = teamMember.getTeam();

        return new ParticipantTeamRes(
                team.getPublicId(),
                team.getContest().getPublicId(),
                team.getContest().getTitle(),
                team.getName(),
                teamMember.getRole(),
                team.getMemberCount(),
                members,
                team.getStatus(),
                team.getRevisionReason(),
                team.getParticipationFinalizedAt());
    }
}
