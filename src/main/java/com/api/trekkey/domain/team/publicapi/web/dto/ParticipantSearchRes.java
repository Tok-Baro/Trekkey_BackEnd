package com.api.trekkey.domain.team.publicapi.web.dto;

import com.api.trekkey.domain.user.entity.User;

public record ParticipantSearchRes(
        Long userId,
        String name,
        String studentId,
        String major
) {
    public static ParticipantSearchRes from(User user) {
        return new ParticipantSearchRes(
                user.getId(),
                user.getName(),
                user.getStudentId(),
                user.getMajor());
    }
}
