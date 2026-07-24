package com.api.trekkey.domain.credential.service.dto;

public record StatusChangeCommand(
        Action action,
        String replacementCredentialPublicId,
        int issuerKeyVersion,
        String reasonCode,
        String reasonDetail) {

    public enum Action {
        REVOKE,
        SUPERSEDE
    }
}
