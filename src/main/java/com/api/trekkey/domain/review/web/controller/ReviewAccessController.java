package com.api.trekkey.domain.review.web.controller;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewAccessService;
import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAccessRes;
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
public class ReviewAccessController {

    private final ReviewAccessService reviewAccessService;

    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = "REVIEW_LINK_INVALID")
    @PostMapping("/access")
    public ResponseEntity<SuccessResponse<ReviewAccessRes>> verifyAccess(
            @RequestBody ReviewAccessReq req) {
        ReviewAccessRes response = reviewAccessService.verifyAccess(req);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(SuccessResponse.okCustom(response, "심사 링크를 확인했습니다."));
    }
}
