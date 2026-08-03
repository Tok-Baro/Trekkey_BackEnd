package com.api.trekkey.domain.contest.entity;

public enum StageStatus {
    PREPARING,
    OPEN,
    COMPLETED;

    public boolean canTransitionTo(StageStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }
        if (this == nextStatus) {
            return true;
        }
        return (this == PREPARING && nextStatus == OPEN)
                || (this == OPEN && nextStatus == COMPLETED);
    }
}
