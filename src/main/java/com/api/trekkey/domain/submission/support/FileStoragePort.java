package com.api.trekkey.domain.submission.support;

import java.io.InputStream;

/**
 * 제출 파일 저장소 추상화 — MVP는 로컬 디스크, 운영 전환 시 S3 어댑터로 교체한다 (erd-mvp §14 Port 원칙).
 * 저장 중 스트림에서 SHA-256을 함께 계산해 별도 해시 워커 없이 무결성 값을 확정한다.
 */
public interface FileStoragePort {

    // 스트림을 저장하고 storageKey·크기·SHA-256을 반환한다
    StoredFile store(String keyPrefix, String originalName, InputStream inputStream);

    // 저장된 파일을 읽기 스트림으로 연다
    InputStream open(String storageKey);

    // 저장된 파일을 삭제한다 (재제출 시 이전 객체 정리 — 실패는 로그만 남긴다)
    void delete(String storageKey);
}
