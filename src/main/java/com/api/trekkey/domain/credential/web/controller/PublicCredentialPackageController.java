package com.api.trekkey.domain.credential.web.controller;

import com.api.trekkey.domain.credential.service.CredentialPackageService;
import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/credentials")
public class PublicCredentialPackageController {

    private final CredentialPackageService credentialPackageService;

    // 공개 subject만 가진 Portable Package — PRIVATE subject가 하나라도 있으면 service에서 거부한다.
    @GetMapping("/{credentialPublicId}/package")
    public ResponseEntity<byte[]> downloadPackage(@PathVariable String credentialPublicId) {
        CredentialPackageFile packageFile = credentialPackageService.buildPackage(credentialPublicId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(packageFile.fileName())
                        .build()
                        .toString())
                .body(packageFile.zipBytes());
    }
}
