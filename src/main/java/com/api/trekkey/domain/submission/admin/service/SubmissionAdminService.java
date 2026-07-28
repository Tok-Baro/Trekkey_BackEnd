package com.api.trekkey.domain.submission.admin.service;

import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.support.FileDownload;
import java.util.List;

public interface SubmissionAdminService {

    // 대회별 제출물 목록 — 같은 조직 관리자만 조회할 수 있다
    List<SubmissionRes> getSubmissions(Long adminUserId, String contestPublicId);

    // 제출 파일 다운로드 — 같은 조직 관리자만
    FileDownload downloadFile(Long adminUserId, Long fileId);
}
