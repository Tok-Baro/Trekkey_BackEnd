package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.web.dto.CredentialHistoryRes;
import java.util.List;

public interface CredentialHistoryService {

    List<CredentialHistoryRes> getMyCredentials(Long userId);

    List<CredentialHistoryRes> getStudentCredentials(Long adminUserId, String studentId);
}
