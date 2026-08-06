package com.api.trekkey.domain.invitation.web.controller;

import com.api.trekkey.domain.invitation.exception.AdminInvitationErrorResponseCode;
import com.api.trekkey.domain.invitation.service.AdminInvitationService;
import com.api.trekkey.domain.invitation.web.dto.request.AdminApprovalReq;
import com.api.trekkey.domain.invitation.web.dto.request.AdminInvitationCreateReq;
import com.api.trekkey.domain.invitation.web.dto.response.AdminInvitationRes;
import com.api.trekkey.domain.invitation.web.dto.response.AdminPendingRes;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.response.code.SuccessResponseCode;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ROOT_ADMIN 전용 콘솔 — URL 규칙(/api/root/** hasRole ROOT_ADMIN)과 메서드 계층 @PreAuthorize 이중화 (설계 §6)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/root")
@PreAuthorize("hasRole('ROOT_ADMIN')")
public class RootAdminController {

    private final AdminInvitationService adminInvitationService;

    @ApiErrorCodeExamples(
            value = UserErrorResponseCode.class,
            codes = {"USER_INVALID_CREDENTIALS"}
    )
    @ApiErrorCodeExamples(
            value = AdminInvitationErrorResponseCode.class,
            codes = {"INVITATION_DUPLICATED"}
    )
    @PostMapping("/invitations")
    public ResponseEntity<SuccessResponse<AdminInvitationRes>> createInvitation(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody @Valid AdminInvitationCreateReq adminInvitationCreateReq) {
        AdminInvitationRes response =
                adminInvitationService.createInvitation(principal.getId(), adminInvitationCreateReq);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.of(response, SuccessResponseCode.SUCCESS_CREATED, "관리자 초대를 발급했습니다."));
    }

    @ApiErrorCodeExamples(
            value = UserErrorResponseCode.class,
            codes = {"USER_INVALID_CREDENTIALS"}
    )
    @GetMapping("/invitations")
    public ResponseEntity<SuccessResponse<List<AdminInvitationRes>>> getInvitations(
            @AuthenticationPrincipal AuthPrincipal principal) {
        List<AdminInvitationRes> response = adminInvitationService.getInvitations(principal.getId());

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @ApiErrorCodeExamples(
            value = AdminInvitationErrorResponseCode.class,
            codes = {"INVITATION_INVALID", "INVITATION_ALREADY_USED"}
    )
    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<SuccessResponse<?>> revokeInvitation(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long invitationId) {
        adminInvitationService.revokeInvitation(principal.getId(), invitationId);

        return ResponseEntity.ok(SuccessResponse.emptyCustom("초대를 철회했습니다."));
    }

    @ApiErrorCodeExamples(
            value = UserErrorResponseCode.class,
            codes = {"USER_INVALID_CREDENTIALS"}
    )
    @GetMapping("/admin-approvals")
    public ResponseEntity<SuccessResponse<List<AdminPendingRes>>> getPendingAdmins(
            @AuthenticationPrincipal AuthPrincipal principal) {
        List<AdminPendingRes> response = adminInvitationService.getPendingAdmins(principal.getId());

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @ApiErrorCodeExamples(
            value = AdminInvitationErrorResponseCode.class,
            codes = {"APPROVAL_TARGET_INVALID"}
    )
    @PatchMapping("/admin-approvals/{userId}")
    public ResponseEntity<SuccessResponse<AdminPendingRes>> decideApproval(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long userId,
            @RequestBody @Valid AdminApprovalReq adminApprovalReq) {
        AdminPendingRes response =
                adminInvitationService.decideApproval(principal.getId(), userId, adminApprovalReq);

        String message = Boolean.TRUE.equals(adminApprovalReq.approve())
                ? "관리자 가입을 승인했습니다."
                : "관리자 가입을 거절했습니다.";
        return ResponseEntity.ok(SuccessResponse.okCustom(response, message));
    }
}
