package com.api.trekkey.domain.award.entity;

public enum AwardStatus {
    /*
        CANDIDATE : 수상 후보 (산출됨, 확정 전)
        CONFIRMED : 수상 확정 — Credential 발급 원천 (erd-mvp §6)
        HELD : 보류
     */
    CANDIDATE,
    CONFIRMED,
    HELD
}
