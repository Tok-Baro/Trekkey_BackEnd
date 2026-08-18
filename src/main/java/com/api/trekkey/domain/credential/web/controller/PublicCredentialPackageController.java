package com.api.trekkey.domain.credential.web.controller;

import com.api.trekkey.domain.credential.service.CredentialCertificateService;
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
    private final CredentialCertificateService credentialCertificateService;

    // 공개 검증 package — canonical 원문·file manifest는 제외하고 공개 허용 요약과 proof만 제공한다.
    @GetMapping("/{credentialPublicId}/package")
    public ResponseEntity<byte[]> downloadPackage(@PathVariable String credentialPublicId) {
        CredentialPackageFile packageFile = credentialPackageService.buildPublicPackage(credentialPublicId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(packageFile.fileName())
                        .build()
                        .toString())
                .body(packageFile.zipBytes());
    }

    // 상장·확인서 PDF — 패키지와 동일 접근 규칙 (erd-mvp §12, QR에는 검증 URL만)
    @GetMapping("/{credentialPublicId}/certificate")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable String credentialPublicId) {
        CredentialPackageFile certificate = credentialCertificateService.renderCertificate(credentialPublicId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(certificate.fileName())
                        .build()
                        .toString())
                .body(certificate.zipBytes());
    }
}
