package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;

public interface CredentialVerificationService {

    CredentialVerificationView verify(String credentialPublicId);
}
