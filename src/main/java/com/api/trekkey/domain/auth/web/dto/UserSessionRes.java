package com.api.trekkey.domain.auth.web.dto;

import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;

public record UserSessionRes(
        Long id,
        String name,
        String email,
        UserRole role,
        String studentId,
        String major
) {
    public static UserSessionRes from(User user) {
        return new UserSessionRes(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStudentId(),
                user.getMajor()
        );
    }
}
