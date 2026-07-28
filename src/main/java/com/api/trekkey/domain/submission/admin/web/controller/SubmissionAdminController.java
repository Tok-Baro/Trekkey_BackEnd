package com.api.trekkey.domain.submission.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.submission.admin.service.SubmissionAdminService;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SubmissionAdminController {

    private final SubmissionAdminService submissionAdminService;

    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @GetMapping("/api/admin/contests/{contestPublicId}/submissions")
    public ResponseEntity<SuccessResponse<List<SubmissionRes>>> getSubmissions(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                submissionAdminService.getSubmissions(authPrincipal.getId(), contestPublicId)));
    }

    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {"SUBMISSION_FILE_NOT_FOUND"})
    @GetMapping("/api/admin/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long fileId) {
        FileDownload download = submissionAdminService.downloadFile(authPrincipal.getId(), fileId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .body(new InputStreamResource(download.inputStream()));
    }
}
