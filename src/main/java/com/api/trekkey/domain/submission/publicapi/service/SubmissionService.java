package com.api.trekkey.domain.submission.publicapi.service;

import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.support.FileDownload;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface SubmissionService {

    // 제출/재제출(덮어쓰기) — 팀 대표만, 제출 단계 OPEN·잠금 전까지. 파일은 전량 교체된다. (erd-mvp §2·§5)
    SubmissionRes submit(Long userId, String teamPublicId, String title, List<MultipartFile> files);

    // 내 팀 제출물 조회 — 팀 대표만
    SubmissionRes getMySubmission(Long userId, String teamPublicId);

    // 제출 파일 다운로드 — 팀 대표만
    FileDownload downloadFile(Long userId, Long fileId);
}
