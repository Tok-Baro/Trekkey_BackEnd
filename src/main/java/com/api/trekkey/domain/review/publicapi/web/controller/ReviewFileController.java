package com.api.trekkey.domain.review.publicapi.web.controller;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.publicapi.service.ReviewFileService;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.submission.support.FileDownload;
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
    @PostMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            @RequestBody ReviewAccessReq req
    ) {
        FileDownload download =
                reviewFileService.downloadFile(fileId, req);

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
