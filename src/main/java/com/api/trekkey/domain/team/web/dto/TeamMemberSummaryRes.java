package com.api.trekkey.domain.team.web.dto;

import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;

public record TeamMemberSummaryRes(
        Long userId,
        String name,
        String studentId,
        String major,
        TeamMemberRole role
) {
    public static TeamMemberSummaryRes from(TeamMember teamMember) {
        return new TeamMemberSummaryRes(
                teamMember.getUser().getId(),
                teamMember.getUser().getName(),
                teamMember.getUser().getStudentId(),
                teamMember.getUser().getMajor(),
                teamMember.getRole());
    }
}
