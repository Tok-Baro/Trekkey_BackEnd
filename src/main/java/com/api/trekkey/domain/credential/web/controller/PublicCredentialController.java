package com.api.trekkey.domain.credential.web.controller;

import com.api.trekkey.domain.credential.service.CredentialVerificationService;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/credentials")
public class PublicCredentialController {

    private final CredentialVerificationService credentialVerificationService;

    @GetMapping("/{credentialPublicId}")
    public ResponseEntity<SuccessResponse<CredentialVerificationView>> verify(
            @PathVariable String credentialPublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(credentialVerificationService.verify(credentialPublicId)));
    }
}
