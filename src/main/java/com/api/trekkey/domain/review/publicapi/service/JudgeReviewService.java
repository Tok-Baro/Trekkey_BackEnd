package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.publicapi.web.dto.JudgePortalRes;
import com.api.trekkey.domain.review.publicapi.web.dto.ReviewSubmitReq;
import com.api.trekkey.domain.submission.support.FileDownload;

public interface JudgeReviewService {

    // 심사위원 포털 — 토큰으로 본인 확인 후 배정 목록·평가 기준 반환
    JudgePortalRes getPortal(String rawToken);

    // 배정된 제출물의 파일 다운로드
    FileDownload downloadFile(String rawToken, Long fileId);

    // 심사 제출 — 배정당 1건, 제출 후 불변 (erd-mvp §5)
    void submitReview(String rawToken, Long assignmentId, ReviewSubmitReq request);
}
