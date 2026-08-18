package com.api.trekkey.domain.evidence.web.controller;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationCaseStatus;
import com.api.trekkey.domain.evidence.service.EvidenceAdminService;
import com.api.trekkey.domain.evidence.web.dto.*;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/evidence-verifications")
public class EvidenceAdminController {
    private final EvidenceAdminService evidenceAdminService;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<EvidenceSubmissionRes>>> queue(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) VerificationCaseStatus status) {
        return ResponseEntity.ok(SuccessResponse.ok(evidenceAdminService.getQueue(principal.getId(), status)));
    }

    @GetMapping("/{casePublicId}")
    public ResponseEntity<SuccessResponse<EvidenceSubmissionRes>> getCase(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String casePublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(evidenceAdminService.getCase(principal.getId(), casePublicId)));
    }

    @PostMapping("/{casePublicId}/reviews")
    public ResponseEntity<SuccessResponse<EvidenceReviewRes>> review(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String casePublicId,
            @RequestBody @Valid EvidenceReviewReq request) {
        return ResponseEntity.ok(SuccessResponse.okCustom(
                evidenceAdminService.review(principal.getId(), casePublicId, request), "검수 의견을 등록했습니다."));
    }

    @GetMapping("/files/{filePublicId}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String filePublicId) {
        FileDownload download = evidenceAdminService.download(principal.getId(), filePublicId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes()).body(new InputStreamResource(download.inputStream()));
    }
}
