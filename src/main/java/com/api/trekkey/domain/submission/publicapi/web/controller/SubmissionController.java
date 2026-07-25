package com.api.trekkey.domain.submission.publicapi.web.controller;

import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.service.SubmissionService;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class SubmissionController {

    private final SubmissionService submissionService;

    // 제출/재제출 — multipart(title + files). 재제출 시 파일 전량 교체 (erd-mvp §5 덮어쓰기)
    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {"TEAM_NOT_FOUND"})
    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_FORBIDDEN",
            "SUBMISSION_NOT_OPEN",
            "SUBMISSION_FINALIZED",
            "SUBMISSION_FILE_REQUIRED",
            "SUBMISSION_FILE_TYPE_INVALID"
    })
    @PutMapping(value = "/api/teams/{teamPublicId}/submission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<SubmissionRes>> submit(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String teamPublicId,
            @RequestParam @NotBlank(message = "작품명을 입력해주세요.")
            @Size(max = 150, message = "작품명은 150자 이하로 입력해주세요.") String title,
            @RequestPart("files") List<MultipartFile> files) {
        SubmissionRes response =
                submissionService.submit(authPrincipal.getId(), teamPublicId, title, files);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "제출물을 접수했습니다."));
    }

    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_NOT_FOUND", "SUBMISSION_FORBIDDEN"})
    @GetMapping("/api/teams/{teamPublicId}/submission")
    public ResponseEntity<SuccessResponse<SubmissionRes>> getMySubmission(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String teamPublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                submissionService.getMySubmission(authPrincipal.getId(), teamPublicId)));
    }

    // 제출 파일 다운로드 — 바이너리 스트리밍 (SuccessResponse 래핑 대상 아님)
    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_FILE_NOT_FOUND", "SUBMISSION_FORBIDDEN"})
    @GetMapping("/api/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long fileId) {
        FileDownload download = submissionService.downloadFile(authPrincipal.getId(), fileId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .body(new InputStreamResource(download.inputStream()));
    }
}
