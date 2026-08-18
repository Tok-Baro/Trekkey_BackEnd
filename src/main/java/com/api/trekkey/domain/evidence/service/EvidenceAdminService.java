package com.api.trekkey.domain.evidence.service;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationCaseStatus;
import com.api.trekkey.domain.evidence.web.dto.EvidenceReviewReq;
import com.api.trekkey.domain.evidence.web.dto.EvidenceReviewRes;
import com.api.trekkey.domain.evidence.web.dto.EvidenceSubmissionRes;
import com.api.trekkey.domain.submission.support.FileDownload;
import java.util.List;

public interface EvidenceAdminService {
    List<EvidenceSubmissionRes> getQueue(Long adminId, VerificationCaseStatus status);
    EvidenceSubmissionRes getCase(Long adminId, String casePublicId);
    EvidenceReviewRes review(Long adminId, String casePublicId, EvidenceReviewReq request);
    FileDownload download(Long adminId, String filePublicId);
}
