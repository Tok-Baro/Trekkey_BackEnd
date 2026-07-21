package com.api.trekkey.domain.invitation.service;

import com.api.trekkey.domain.invitation.web.dto.request.AdminApprovalReq;
import com.api.trekkey.domain.invitation.web.dto.request.AdminInvitationCreateReq;
import com.api.trekkey.domain.invitation.web.dto.response.AdminInvitationRes;
import com.api.trekkey.domain.invitation.web.dto.response.AdminPendingRes;
import java.util.List;

public interface AdminInvitationService {

    // 관리자 초대 발급 — 초대 URL은 이 응답에만 포함된다 (로그 금지)
    AdminInvitationRes createInvitation(Long rootUserId, AdminInvitationCreateReq req);

    // 자기 조직의 초대 목록 (ISSUED·만료 건은 lazy expire 후 반환)
    List<AdminInvitationRes> getInvitations(Long rootUserId);

    // 초대 철회 (초대 URL 유출 대응)
    void revokeInvitation(Long rootUserId, Long invitationId);

    // 승인 대기 관리자 목록 (7일 경과 건은 lazy 거절 처리 후 제외)
    List<AdminPendingRes> getPendingAdmins(Long rootUserId);

    // 관리자 가입 승인/거절
    AdminPendingRes decideApproval(Long rootUserId, Long targetUserId, AdminApprovalReq req);
}
