package com.api.trekkey.domain.credential.web.dto;

import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import java.time.LocalDateTime;
import java.util.List;

public record PublicActivityProfileRes(
        String publicId,
        String displayName,
        String major,
        String organizationName,
        int credentialCount,
        List<Activity> activities) {

    public PublicActivityProfileRes {
        activities = List.copyOf(activities);
    }

    public record Activity(
            String credentialPublicId,
            String credentialNo,
            CredentialType credentialType,
            CredentialStatus status,
            String roleCode,
            String contestTitle,
            LocalDateTime issuedAt) {
    }
}
