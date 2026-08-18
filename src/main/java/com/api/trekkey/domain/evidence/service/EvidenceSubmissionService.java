package com.api.trekkey.domain.evidence.service;

import com.api.trekkey.domain.evidence.web.dto.EvidenceSubmissionCreateReq;
import com.api.trekkey.domain.evidence.web.dto.EvidenceSubmissionRes;
import com.api.trekkey.domain.submission.support.FileDownload;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface EvidenceSubmissionService {
    EvidenceSubmissionRes submit(Long userId, EvidenceSubmissionCreateReq request, List<MultipartFile> files);
    List<EvidenceSubmissionRes> getMine(Long userId);
    EvidenceSubmissionRes getMine(Long userId, String publicId);
    FileDownload download(Long userId, String filePublicId);
}
