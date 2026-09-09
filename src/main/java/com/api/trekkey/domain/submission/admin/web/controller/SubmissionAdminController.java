package com.api.trekkey.domain.submission.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.submission.admin.service.SubmissionAdminService;
import com.api.trekkey.domain.submission.publicapi.service.SubmissionService;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.response.code.SuccessResponseCode;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SubmissionAdminController {

    private final SubmissionAdminService submissionAdminService;
    private final SubmissionService submissionService;

    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {"TEAM_NOT_FOUND"})
    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {"USER_NOT_FOUND", "USER_INVALID_TOKEN"})
    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_NOT_OPEN", "SUBMISSION_FINALIZED", "SUBMISSION_ALREADY_EXISTS",
            "SUBMISSION_FILE_REQUIRED", "SUBMISSION_FILE_TYPE_INVALID", "SUBMISSION_STORAGE_ERROR"})
    @PostMapping(value = "/api/admin/contests/{contestPublicId}/teams/{teamPublicId}/submission",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<SubmissionRes>> receiveSubmission(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String contestPublicId,
            @PathVariable String teamPublicId,
            @RequestParam @NotBlank(message = "작품명을 입력해주세요.")
            @Size(max = 150, message = "작품명은 150자 이하로 입력해주세요.") String title,
            @RequestPart("files") List<MultipartFile> files) {
        SubmissionRes response = submissionService.submitByAdmin(
                principal.getId(), contestPublicId, teamPublicId, title, files);
        return ResponseEntity.status(201)
                .body(SuccessResponse.of(response, SuccessResponseCode.SUCCESS_CREATED, "관리자 수동 접수를 완료했습니다."));
    }

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
