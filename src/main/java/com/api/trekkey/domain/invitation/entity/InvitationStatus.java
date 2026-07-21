package com.api.trekkey.domain.invitation.entity;

public enum InvitationStatus {
    /*
        ISSUED : 발급됨 (사용 가능)
        USED : 가입에 사용됨
        EXPIRED : 만료됨 (발급 후 7일 경과)
        REVOKED : ROOT_ADMIN이 철회함
     */
    ISSUED,
    USED,
    EXPIRED,
    REVOKED
}
