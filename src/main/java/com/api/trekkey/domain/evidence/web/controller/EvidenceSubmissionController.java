package com.api.trekkey.domain.evidence.web.controller;

import com.api.trekkey.domain.evidence.service.EvidenceSubmissionService;
import com.api.trekkey.domain.evidence.web.dto.*;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
@RequestMapping("/api/me")
public class EvidenceSubmissionController {
    private final EvidenceSubmissionService evidenceSubmissionService;

    @PostMapping(value = "/evidence-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<EvidenceSubmissionRes>> submit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("request") @Valid EvidenceSubmissionCreateReq request,
            @RequestPart(value = "file", required = false) MultipartFile legacyFile,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        List<MultipartFile> bundle = new ArrayList<>();
        if (legacyFile != null) bundle.add(legacyFile);
        if (files != null) bundle.addAll(files);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.create(
                evidenceSubmissionService.submit(principal.getId(), request, bundle)));
    }

    @GetMapping("/evidence-submissions")
    public ResponseEntity<SuccessResponse<List<EvidenceSubmissionRes>>> getMine(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(evidenceSubmissionService.getMine(principal.getId())));
    }

    @GetMapping("/evidence-submissions/{publicId}")
    public ResponseEntity<SuccessResponse<EvidenceSubmissionRes>> getMine(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String publicId) {
        return ResponseEntity.ok(SuccessResponse.ok(evidenceSubmissionService.getMine(principal.getId(), publicId)));
    }

    @GetMapping("/evidence-files/{filePublicId}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String filePublicId) {
        return response(evidenceSubmissionService.download(principal.getId(), filePublicId));
    }

    private ResponseEntity<Resource> response(FileDownload download) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes()).body(new InputStreamResource(download.inputStream()));
    }
}
