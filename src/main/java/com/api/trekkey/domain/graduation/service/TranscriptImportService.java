package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.graduation.web.dto.TranscriptImportRes;
import org.springframework.web.multipart.MultipartFile;

public interface TranscriptImportService {
    TranscriptImportRes importTranscript(Long userId, MultipartFile file, boolean apply);
}
