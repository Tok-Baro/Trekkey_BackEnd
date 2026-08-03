package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;

public interface CredentialCertificateService {

    CredentialPackageFile renderCertificate(String credentialPublicId);
}
