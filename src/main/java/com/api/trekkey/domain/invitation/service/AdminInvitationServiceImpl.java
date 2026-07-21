package com.api.trekkey.domain.invitation.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.invitation.entity.AdminInvitation;
import com.api.trekkey.domain.invitation.entity.InvitationStatus;
import com.api.trekkey.domain.invitation.exception.AdminInvitationErrorResponseCode;
import com.api.trekkey.domain.invitation.repository.AdminInvitationRepository;
import com.api.trekkey.domain.invitation.web.dto.request.AdminApprovalReq;
import com.api.trekkey.domain.invitation.web.dto.request.AdminInvitationCreateReq;
import com.api.trekkey.domain.invitation.web.dto.response.AdminInvitationRes;
import com.api.trekkey.domain.invitation.web.dto.response.AdminPendingRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminInvitationServiceImpl implements AdminInvitationService {

    private static final int INVITATION_EXPIRE_DAYS = 7; //초대 만료 7일 (설계 §2-4)
    private static final int APPROVAL_EXPIRE_DAYS = 7; //승인 대기 만료 7일 (설계 §2-3)

    private static final String TARGET_TYPE_INVITATION = "INVITATION";
    private static final String TARGET_TYPE_USER = "USER";

    private final UserRepository userRepository;
    private final AdminInvitationRepository adminInvitationRepository;
    private final AdminAuditLogger adminAuditLogger;

    @Value("${app.front.base-url}")
    private String frontBaseUrl;

    @Override
    public AdminInvitationRes createInvitation(Long rootUserId, AdminInvitationCreateReq req) {
        User rootUser = findRootUser(rootUserId);
        LocalDateTime now = LocalDateTime.now();
        Long organizationId = rootUser.getOrganization().getId();

        // 같은 조직 초대를 훑으며 ISSUED&만료 건은 lazy expire, 사용 가능한 동일 이메일 초대는 중복으로 차단
        List<AdminInvitation> invitations =
                adminInvitationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
        expireOutdated(invitations, now);
        boolean duplicated = invitations.stream()
                .anyMatch(invitation -> invitation.getEmail().equals(req.email()) && invitation.isUsable(now));
        if (duplicated) {
            throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_DUPLICATED);
        }

        String token = UUID.randomUUID().toString(); //원문은 저장·로그하지 않는다
        AdminInvitation invitation = adminInvitationRepository.save(AdminInvitation.builder()
                .organization(rootUser.getOrganization())
                .email(req.email())
                .tokenHash(hash(token))
                .invitedBy(rootUser)
                .expiresAt(now.plusDays(INVITATION_EXPIRE_DAYS))
                .build());

        //초대 URL은 응답에만 포함한다 — 절대 로그 금지 (설계 §7)
        String inviteUrl = frontBaseUrl + "/signup/admin?token=" + token;

        adminAuditLogger.log(rootUser.getId(), organizationId, AuditAction.INVITATION_ISSUE,
                TARGET_TYPE_INVITATION, invitation.getId(), req.email());

        return AdminInvitationRes.of(invitation, inviteUrl);
    }

    @Override
    public List<AdminInvitationRes> getInvitations(Long rootUserId) {
        User rootUser = findRootUser(rootUserId);
        LocalDateTime now = LocalDateTime.now();

        List<AdminInvitation> invitations = adminInvitationRepository
                .findByOrganizationIdOrderByCreatedAtDesc(rootUser.getOrganization().getId());
        expireOutdated(invitations, now); //dirty checking으로 상태 반영

        return invitations.stream()
                .map(AdminInvitationRes::from)
                .toList();
    }

    @Override
    public void revokeInvitation(Long rootUserId, Long invitationId) {
        User rootUser = findRootUser(rootUserId);

        AdminInvitation invitation = adminInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(AdminInvitationErrorResponseCode.INVITATION_INVALID));

        if (!invitation.getOrganization().getId().equals(rootUser.getOrganization().getId())) {
            throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_INVALID);
        }
        if (invitation.getStatus() != InvitationStatus.ISSUED) {
            if (invitation.getStatus() == InvitationStatus.USED) {
                throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_ALREADY_USED);
            }
            throw new CustomException(AdminInvitationErrorResponseCode.INVITATION_INVALID);
        }

        invitation.revoke();

        adminAuditLogger.log(rootUser.getId(), rootUser.getOrganization().getId(), AuditAction.INVITATION_REVOKE,
                TARGET_TYPE_INVITATION, invitation.getId(), invitation.getEmail());
    }

    @Override
    public List<AdminPendingRes> getPendingAdmins(Long rootUserId) {
        User rootUser = findRootUser(rootUserId);
        LocalDateTime now = LocalDateTime.now();
        Long organizationId = rootUser.getOrganization().getId();

        List<User> pendingUsers = userRepository.findByOrganizationIdAndRoleAndStatus(
                organizationId, UserRole.ADMIN, UserStatus.PENDING_APPROVAL);

        List<AdminPendingRes> result = new ArrayList<>();
        for (User pendingUser : pendingUsers) {
            // 승인 대기 7일 경과 → 자동 거절 (lazy — 방치된 대기 계정이 공격면이 되는 것 방지, 설계 §2-3)
            if (pendingUser.getCreatedAt().plusDays(APPROVAL_EXPIRE_DAYS).isBefore(now)) {
                pendingUser.reject();
                adminAuditLogger.log(rootUser.getId(), organizationId, AuditAction.ADMIN_REJECT,
                        TARGET_TYPE_USER, pendingUser.getId(), "승인 기한 만료");
                continue;
            }
            result.add(AdminPendingRes.from(pendingUser));
        }
        return result;
    }

    @Override
    public AdminPendingRes decideApproval(Long rootUserId, Long targetUserId, AdminApprovalReq req) {
        User rootUser = findRootUser(rootUserId);
        Long organizationId = rootUser.getOrganization().getId();

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(AdminInvitationErrorResponseCode.APPROVAL_TARGET_INVALID));

        if (!target.getOrganization().getId().equals(organizationId)
                || target.getRole() != UserRole.ADMIN
                || target.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new CustomException(AdminInvitationErrorResponseCode.APPROVAL_TARGET_INVALID);
        }

        if (Boolean.TRUE.equals(req.approve())) {
            target.approve();
            adminAuditLogger.log(rootUser.getId(), organizationId, AuditAction.ADMIN_APPROVE,
                    TARGET_TYPE_USER, target.getId(), "status: PENDING_APPROVAL→ACTIVE");
        } else {
            target.reject();
            adminAuditLogger.log(rootUser.getId(), organizationId, AuditAction.ADMIN_REJECT,
                    TARGET_TYPE_USER, target.getId(), "status: PENDING_APPROVAL→INACTIVE");
        }

        return AdminPendingRes.from(target);
    }

    //======= 헬퍼 메서드 ==========

    private User findRootUser(Long rootUserId) {
        return userRepository.findById(rootUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_INVALID_CREDENTIALS));
    }

    // ISSUED인데 만료 시각이 지난 초대를 EXPIRED로 전이한다. (lazy expire — dirty checking으로 반영)
    private void expireOutdated(List<AdminInvitation> invitations, LocalDateTime now) {
        invitations.stream()
                .filter(invitation -> invitation.getStatus() == InvitationStatus.ISSUED
                        && invitation.isExpired(now))
                .forEach(AdminInvitation::expire);
    }

    // 초대 토큰 SHA-256 해시 — 원문 미저장 (AuthServiceImpl의 RefreshToken 해시와 동일 패턴)
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
