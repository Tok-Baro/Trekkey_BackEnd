package com.api.trekkey.domain.review.entity;

public enum ReviewRoundStatus {
    PREPARING,
    OPEN,
    FINALIZED;

    public boolean canTransitionTo(ReviewRoundStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }
        if (this == nextStatus) {
            return true;
        }
        return (this == PREPARING && nextStatus == OPEN)
                || (this == OPEN && nextStatus == FINALIZED);
    }
}
