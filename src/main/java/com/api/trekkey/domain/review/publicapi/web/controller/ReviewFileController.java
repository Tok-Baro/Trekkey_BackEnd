package com.api.trekkey.domain.review.publicapi.web.controller;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.publicapi.service.ReviewFileService;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review/files")
public class ReviewFileController {

    private final ReviewFileService reviewFileService;

    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = {
                    "REVIEW_LINK_INVALID",
                    "REVIEW_ASSIGNMENT_NOT_FOUND"
            })
    @PostMapping(
            value = "/{fileId}/download/check",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<?>> validateFileAccess(
            @PathVariable Long fileId,
            @RequestBody ReviewAccessReq req
    ) {
        reviewFileService.validateFileAccess(fileId, req);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(SuccessResponse.emptyCustom(
                        "파일 다운로드 권한을 확인했습니다."));
    }

    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = {
                    "REVIEW_LINK_INVALID",
                    "REVIEW_ASSIGNMENT_NOT_FOUND"
            })
    @PostMapping(
            value = "/{fileId}/download",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            @RequestBody ReviewAccessReq req
    ) {
        return downloadResponse(
                reviewFileService.downloadFile(fileId, req));
    }

    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = {
                    "REVIEW_LINK_INVALID",
                    "REVIEW_ASSIGNMENT_NOT_FOUND"
            })
    @PostMapping(
            value = "/{fileId}/download",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Resource> downloadFileFromNativeForm(
            @PathVariable Long fileId,
            @RequestParam String token
    ) {
        return downloadResponse(reviewFileService.downloadFile(
                fileId,
                new ReviewAccessReq(token)
        ));
    }

    private ResponseEntity<Resource> downloadResponse(
            FileDownload download
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        download.originalName(),
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        download.contentType()))
                .contentLength(download.sizeBytes())
                .body(new InputStreamResource(
                        download.inputStream()));
    }
}
