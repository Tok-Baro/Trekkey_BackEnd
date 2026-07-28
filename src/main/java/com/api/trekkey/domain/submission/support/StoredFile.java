package com.api.trekkey.domain.submission.support;

// 저장소에 기록된 파일의 결과 — 키와 저장 스트림에서 계산한 SHA-256(hex)
public record StoredFile(
        String storageKey,
        long sizeBytes,
        String sha256
) {
}
