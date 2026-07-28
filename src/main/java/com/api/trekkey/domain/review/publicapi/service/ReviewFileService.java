package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.submission.support.FileDownload;

public interface ReviewFileService {

    FileDownload downloadFile(
            Long fileId,
            ReviewAccessReq req
    );
}
