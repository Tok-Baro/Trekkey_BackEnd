package com.api.trekkey.domain.submission.entity;

public enum IntegrityStatus {
    /*
        STALE : 파일이 교체되어 해시 재계산 대기 (비동기 해시 전환 대비)
        READY : 전체 파일의 SHA-256 확정 — Credential 발급 가능 상태
     */
    STALE,
    READY
}
