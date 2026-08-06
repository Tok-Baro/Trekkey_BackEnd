package com.api.trekkey.domain.review.publicapi.web.controller;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.publicapi.service.ReviewSubmissionService;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSubmitRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review/assignments")
public class ReviewSubmissionController {

    private final ReviewSubmissionService reviewSubmissionService;

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_LINK_INVALID",
            "REVIEW_ASSIGNMENT_NOT_FOUND",
            "REVIEW_SUBMISSION_NOT_ALLOWED",
            "REVIEW_SUBMISSION_NOT_OPEN",
            "REVIEW_SUBMISSION_DEADLINE_EXPIRED",
            "REVIEW_SCORE_CRITERIA_MISMATCH",
            "REVIEW_SCORE_OUT_OF_RANGE",
            "REVIEW_COMMENT_TOO_LONG",
            "REVIEW_ALREADY_SUBMITTED",
            "REVIEW_SUBMISSION_STATE_INVALID",
            "REVIEW_SUBMISSION_DUPLICATED"
    })
    @PutMapping("/{assignmentId}/review")
    public ResponseEntity<SuccessResponse<ReviewSubmitRes>> submitReview(
            @PathVariable Long assignmentId,
            @RequestBody @Valid ReviewSubmitReq req) {
        ReviewSubmitRes response =
                reviewSubmissionService.submitReview(
                        assignmentId,
                        req
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(SuccessResponse.okCustom(
                        response,
                        "채점 결과를 제출했습니다."
                ));
    }
}
