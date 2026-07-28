package com.api.trekkey.domain.invitation.web.dto.response;

import com.api.trekkey.domain.user.entity.User;
import java.time.LocalDateTime;

public record AdminPendingRes(
        Long userId,
        String name,
        String email,
        String department,
        String position,
        LocalDateTime appliedAt //가입 신청 시각 (createdAt)
) {
    public static AdminPendingRes from(User user) {
        return new AdminPendingRes(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDepartment(),
                user.getPosition(),
                user.getCreatedAt()
        );
    }
}
