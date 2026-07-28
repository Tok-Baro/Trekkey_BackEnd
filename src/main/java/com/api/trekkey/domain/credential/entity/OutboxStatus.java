package com.api.trekkey.domain.credential.entity;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    DEAD
}
