package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;

public interface CredentialPackageService {

    CredentialPackageFile buildPackage(String credentialPublicId);
}
