package com.api.trekkey.domain.credential.web.controller;

import com.api.trekkey.domain.credential.service.CredentialAdminQueryService;
import com.api.trekkey.domain.credential.web.dto.CredentialSummaryRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCredentialQueryController {

    private final CredentialAdminQueryService credentialAdminQueryService;

    // 대회 발급 현황 — 참여·작품·수상 credential 전체 (erd-mvp §13)
    @GetMapping("/api/admin/contests/{contestPublicId}/credentials")
    public ResponseEntity<SuccessResponse<List<CredentialSummaryRes>>> getContestCredentials(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialAdminQueryService.getContestCredentials(authPrincipal.getId(), contestPublicId)));
    }

    // 팀 발급 현황
    @GetMapping("/api/admin/teams/{teamPublicId}/credentials")
    public ResponseEntity<SuccessResponse<List<CredentialSummaryRes>>> getTeamCredentials(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String teamPublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialAdminQueryService.getTeamCredentials(authPrincipal.getId(), teamPublicId)));
    }
}
