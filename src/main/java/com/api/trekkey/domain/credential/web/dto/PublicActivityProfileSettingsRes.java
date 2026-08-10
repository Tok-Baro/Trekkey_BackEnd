package com.api.trekkey.domain.credential.web.dto;

public record PublicActivityProfileSettingsRes(
        boolean enabled,
        String publicId,
        int publicCredentialCount) {

    public static PublicActivityProfileSettingsRes disabled(int publicCredentialCount) {
        return new PublicActivityProfileSettingsRes(false, null, publicCredentialCount);
    }
}
