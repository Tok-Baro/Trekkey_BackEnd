package com.api.trekkey.domain.credential.entity;

public enum OutboxAggregateType {
    CREDENTIAL,
    BATCH,
    STATUS_EVENT,
    ISSUER_KEY
}
