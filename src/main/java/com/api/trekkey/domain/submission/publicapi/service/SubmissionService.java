package com.api.trekkey.domain.submission.publicapi.service;

import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionSaveReq;

public interface SubmissionService {

    SubmissionRes getSubmission(Long userId, String contestPublicId);

    SubmissionRes saveDraft(
            Long userId,
            String contestPublicId,
            SubmissionSaveReq request);

    SubmissionRes submit(Long userId, String contestPublicId);

    SubmissionRes reopen(Long userId, String contestPublicId);

    SubmissionRes withdraw(Long userId, String contestPublicId);
}
