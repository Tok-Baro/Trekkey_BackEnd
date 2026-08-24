package com.api.trekkey.domain.graduation.web.controller;

import com.api.trekkey.domain.graduation.service.ActivityImportService;
import com.api.trekkey.domain.graduation.web.dto.ActivityImportRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class ActivityImportController {
    private final ActivityImportService importService;

    @PostMapping("/api/me/graduation/activity-imports")
    public ResponseEntity<SuccessResponse<ActivityImportRes>> importActivities(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean apply) {
        return ResponseEntity.ok(SuccessResponse.ok(importService.importActivities(principal.getId(), file, apply)));
    }
}
