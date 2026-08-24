package com.api.trekkey.domain.graduation.web.controller;

import com.api.trekkey.domain.graduation.service.TranscriptImportService;
import com.api.trekkey.domain.graduation.web.dto.TranscriptImportRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class TranscriptImportController {
    private final TranscriptImportService transcriptImportService;

    @PostMapping("/api/me/graduation/transcript-imports")
    public ResponseEntity<SuccessResponse<TranscriptImportRes>> importTranscript(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean apply) {
        return ResponseEntity.ok(SuccessResponse.ok(
                transcriptImportService.importTranscript(principal.getId(), file, apply)));
    }
}
