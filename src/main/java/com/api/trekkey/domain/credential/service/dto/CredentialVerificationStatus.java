package com.api.trekkey.domain.credential.service.dto;

public enum CredentialVerificationStatus {
    VALID,
    PENDING,
    REVOKED,
    SUPERSEDED,
    EXPIRED,
    TAMPERED,
    ANCHOR_NOT_FOUND,
    ISSUER_INVALID,
    RPC_UNAVAILABLE,
    BLOCKCHAIN_CONFIGURATION_ERROR,
    SCHEMA_UNSUPPORTED
}
