package com.api.trekkey.domain.graduation.web.controller;

import com.api.trekkey.domain.graduation.service.GraduationProfileService;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileReq;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class GraduationProfileController {
    private final GraduationProfileService graduationProfileService;

    @GetMapping("/api/me/graduation/profile")
    public ResponseEntity<SuccessResponse<GraduationProfileRes>> get(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(graduationProfileService.get(principal.getId())));
    }

    @PutMapping("/api/me/graduation/profile")
    public ResponseEntity<SuccessResponse<GraduationProfileRes>> save(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody @Valid GraduationProfileReq request) {
        return ResponseEntity.ok(SuccessResponse.ok(graduationProfileService.save(principal.getId(), request)));
    }
}
