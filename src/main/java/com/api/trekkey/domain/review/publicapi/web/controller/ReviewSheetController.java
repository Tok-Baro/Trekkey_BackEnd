package com.api.trekkey.domain.review.publicapi.web.controller;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.publicapi.service.ReviewSheetService;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewSheetRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review")
public class ReviewSheetController {

    private final ReviewSheetService reviewSheetService;

    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = "REVIEW_LINK_INVALID")
    @PostMapping("/assignments")
    public ResponseEntity<SuccessResponse<ReviewSheetRes>> getReviewSheet(
            @RequestBody ReviewAccessReq req) {
        ReviewSheetRes response =
                reviewSheetService.getReviewSheet(req);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(SuccessResponse.okCustom(
                        response,
                        "심사 평가표를 조회했습니다."
                ));
    }
}
