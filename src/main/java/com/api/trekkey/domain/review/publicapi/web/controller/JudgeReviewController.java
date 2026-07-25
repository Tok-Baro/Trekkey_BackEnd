package com.api.trekkey.domain.review.publicapi.web.controller;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.publicapi.service.JudgeReviewService;
import com.api.trekkey.domain.review.publicapi.web.dto.JudgePortalRes;
import com.api.trekkey.domain.review.publicapi.web.dto.ReviewSubmitReq;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 심사위원 링크 API — 로그인 없이 reviewToken으로 본인을 확인한다 (SecurityConfig permitAll)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review")
public class JudgeReviewController {

    private final JudgeReviewService judgeReviewService;

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_TOKEN_INVALID", "REVIEW_TOKEN_EXPIRED"})
    @GetMapping
    public ResponseEntity<SuccessResponse<JudgePortalRes>> getPortal(
            @RequestParam("token") String token) {
        return ResponseEntity.ok(SuccessResponse.ok(judgeReviewService.getPortal(token)));
    }

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_TOKEN_INVALID", "REVIEW_TOKEN_EXPIRED", "ASSIGNMENT_NOT_FOUND"})
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam("token") String token,
            @PathVariable Long fileId) {
        FileDownload download = judgeReviewService.downloadFile(token, fileId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .body(new InputStreamResource(download.inputStream()));
    }

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_TOKEN_INVALID", "REVIEW_TOKEN_EXPIRED", "ASSIGNMENT_NOT_FOUND",
            "ROUND_NOT_OPEN", "REVIEW_ALREADY_SUBMITTED", "SCORE_CRITERION_MISMATCH", "SCORE_OUT_OF_RANGE"})
    @PostMapping("/assignments/{assignmentId}")
    public ResponseEntity<SuccessResponse<?>> submitReview(
            @RequestParam("token") String token,
            @PathVariable Long assignmentId,
            @RequestBody @Valid ReviewSubmitReq request) {
        judgeReviewService.submitReview(token, assignmentId, request);

        return ResponseEntity.ok(SuccessResponse.emptyCustom("심사를 제출했습니다."));
    }
}
