package com.api.trekkey.domain.credential.web.controller;

import com.api.trekkey.domain.credential.service.CredentialHistoryService;
import com.api.trekkey.domain.credential.web.dto.CredentialHistoryRes;
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
public class CredentialHistoryController {

    private final CredentialHistoryService credentialHistoryService;

    // 내 Credential 이력 — 발급 당시 subject snapshot 기준 (erd-mvp §5, 팀 해체와 무관하게 보존)
    @GetMapping("/api/me/credentials") //develop 채택 컨벤션(/api/me/*)으로 통일
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<SuccessResponse<List<CredentialHistoryRes>>> getMyCredentials(
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialHistoryService.getMyCredentials(authPrincipal.getId())));
    }

    // 학번 기반 개인 이력 조회 — 학교 관리자 전용, 자기 조직 발급분만 (erd-mvp §13)
    @GetMapping("/api/admin/students/{studentId}/credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<List<CredentialHistoryRes>>> getStudentCredentials(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String studentId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialHistoryService.getStudentCredentials(authPrincipal.getId(), studentId)));
    }
}
