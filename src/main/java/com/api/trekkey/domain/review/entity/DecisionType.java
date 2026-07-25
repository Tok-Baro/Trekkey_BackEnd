package com.api.trekkey.domain.review.entity;

public enum DecisionType {
    /*
        RULE : 통과 규칙(TOP_N/MIN_SCORE)에 따른 자동 판정
        MANUAL : 관리자 수동 판정 (동점 처리·정정 — erd-mvp §5)
     */
    RULE,
    MANUAL
}
