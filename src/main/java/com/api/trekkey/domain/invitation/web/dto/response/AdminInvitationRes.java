package com.api.trekkey.domain.invitation.web.dto.response;

import com.api.trekkey.domain.invitation.entity.AdminInvitation;
import com.api.trekkey.domain.invitation.entity.InvitationStatus;
import java.time.LocalDateTime;

public record AdminInvitationRes(
        Long id,
        String email,
        InvitationStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        String inviteUrl //발급 응답에만 값 존재 — 목록 조회에서는 null (원문 미저장이므로 재조회 불가)
) {
    // 목록 조회용 — 초대 URL 미포함
    public static AdminInvitationRes from(AdminInvitation invitation) {
        return of(invitation, null);
    }

    // 발급 응답용 — 초대 URL은 이 응답에서만 반환한다 (로그 금지)
    public static AdminInvitationRes of(AdminInvitation invitation, String inviteUrl) {
        return new AdminInvitationRes(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt(),
                inviteUrl
        );
    }
}
