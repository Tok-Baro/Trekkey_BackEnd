package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileRes;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileSettingsRes;

public interface PublicActivityProfileService {

    PublicActivityProfileSettingsRes getSettings(Long userId);

    PublicActivityProfileSettingsRes updateSettings(Long userId, boolean enabled);

    PublicActivityProfileSettingsRes rotateLink(Long userId);

    PublicActivityProfileRes getPublicProfile(String publicId);
}
