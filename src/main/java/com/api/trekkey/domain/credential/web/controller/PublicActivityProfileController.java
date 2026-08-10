package com.api.trekkey.domain.credential.web.controller;

import com.api.trekkey.domain.credential.service.PublicActivityProfileService;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileRes;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileSettingsRes;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileUpdateReq;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PublicActivityProfileController {

    private final PublicActivityProfileService publicActivityProfileService;

    @GetMapping("/api/me/public-activity-profile")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<SuccessResponse<PublicActivityProfileSettingsRes>> getSettings(
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                publicActivityProfileService.getSettings(authPrincipal.getId())));
    }

    @PutMapping("/api/me/public-activity-profile")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<SuccessResponse<PublicActivityProfileSettingsRes>> updateSettings(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @Valid @RequestBody PublicActivityProfileUpdateReq request) {
        return ResponseEntity.ok(SuccessResponse.ok(
                publicActivityProfileService.updateSettings(authPrincipal.getId(), request.enabled())));
    }

    @PostMapping("/api/me/public-activity-profile/rotate")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<SuccessResponse<PublicActivityProfileSettingsRes>> rotateLink(
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                publicActivityProfileService.rotateLink(authPrincipal.getId())));
    }

    @GetMapping("/api/public/activity-profiles/{publicId}")
    public ResponseEntity<SuccessResponse<PublicActivityProfileRes>> getPublicProfile(
            @PathVariable String publicId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                publicActivityProfileService.getPublicProfile(publicId)));
    }
}
