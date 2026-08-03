package com.api.trekkey.domain.contest.entity;

public enum StageType {
    APPLICATION,
    SUBMISSION,
    REVIEW,
    PRESENTATION,
    AWARD;

    public boolean supportsReviewCriteria() {
        return this == REVIEW || this == PRESENTATION;
    }
}
