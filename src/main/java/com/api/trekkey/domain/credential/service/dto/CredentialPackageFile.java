package com.api.trekkey.domain.credential.service.dto;

// Portable Credential Package 산출물 — zip 바이트와 다운로드 파일명 (erd-mvp §12)
public record CredentialPackageFile(
        String fileName,
        byte[] zipBytes) {

    public CredentialPackageFile {
        zipBytes = zipBytes.clone();
    }

    @Override
    public byte[] zipBytes() {
        return zipBytes.clone();
    }
}
