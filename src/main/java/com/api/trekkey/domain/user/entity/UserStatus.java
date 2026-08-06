package com.api.trekkey.domain.user.entity;

public enum UserStatus {
    /*
        PENDING_APPROVAL : 관리자 가입 완료, 승인 대기
        ACTIVE : 활성상태
        GRADUATED : 졸업상태
        WITHDRAWN : 자퇴상태
        TRANSFERRED : 편입
        INACTIVE : 비활성화상태
     */
    PENDING_APPROVAL,
    ACTIVE,
    GRADUATED,
    WITHDRAWN,
    TRANSFERRED,
    INACTIVE
}
