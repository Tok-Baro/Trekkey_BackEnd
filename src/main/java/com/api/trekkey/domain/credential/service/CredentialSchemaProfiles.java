package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.entity.CredentialType;
import java.util.Map;

public final class CredentialSchemaProfiles {

    public static final String PARTICIPATION_V1 =
            "trekkey:participation:v1:jcs-rfc8785:unicode-nfc-1";
    public static final String WORK_V1 =
            "trekkey:work:v1:jcs-rfc8785:unicode-nfc-1";
    public static final String AWARD_V1 =
            "trekkey:award:v1:jcs-rfc8785:unicode-nfc-1";

    private static final Map<CredentialType, String> V1_PROFILES = Map.of(
            CredentialType.PARTICIPATION, PARTICIPATION_V1,
            CredentialType.WORK, WORK_V1,
            CredentialType.AWARD, AWARD_V1);

    private CredentialSchemaProfiles() {
    }

    public static String forType(CredentialType credentialType) {
        return V1_PROFILES.get(credentialType);
    }

    public static boolean supports(CredentialType credentialType, String schemaProfileId) {
        return credentialType != null && V1_PROFILES.get(credentialType).equals(schemaProfileId);
    }
}
