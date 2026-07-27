package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.web.dto.CredentialSummaryRes;
import java.util.List;

public interface CredentialAdminQueryService {

    List<CredentialSummaryRes> getContestCredentials(Long adminUserId, String contestPublicId);

    List<CredentialSummaryRes> getTeamCredentials(Long adminUserId, String teamPublicId);
}
