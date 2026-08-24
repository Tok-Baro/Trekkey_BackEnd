package com.api.trekkey.domain.graduation.web.controller;

import com.api.trekkey.domain.graduation.service.HansungGraduationSourceSyncService;
import com.api.trekkey.domain.graduation.web.dto.GraduationSourceSyncRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class GraduationSourceAdminController {
    private final HansungGraduationSourceSyncService syncService;

    @PostMapping("/api/admin/graduation/sources/sync")
    public ResponseEntity<SuccessResponse<GraduationSourceSyncRes>> sync(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(syncService.sync(principal.getId())));
    }
}
