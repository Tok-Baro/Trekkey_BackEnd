package com.api.trekkey.domain.review.entity;

public enum EntryStatus {
    /*
        ELIGIBLE : 라운드 진입 (심사 대기)
        IN_REVIEW : 심사 진행 중
        PASSED : 통과 확정
        FAILED : 탈락 확정
        WITHDRAWN : 철회
        DISQUALIFIED : 실격
     */
    ELIGIBLE,
    IN_REVIEW,
    PASSED,
    FAILED,
    WITHDRAWN,
    DISQUALIFIED
}
