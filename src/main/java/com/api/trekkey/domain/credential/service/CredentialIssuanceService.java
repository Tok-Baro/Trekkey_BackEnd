package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.IssuedCredential;

public interface CredentialIssuanceService {

    IssuedCredential issue(CredentialIssueCommand command);
}
