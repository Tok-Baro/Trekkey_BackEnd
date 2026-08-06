package com.api.trekkey.domain.review.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.review.admin.service.ReviewRecordAdminService;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRecordRes;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(
        "/api/admin/contests/{publicId}/review-rounds/{roundId}/reviews")
public class ReviewRecordAdminController {

    private final ReviewRecordAdminService reviewRecordAdminService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = "REVIEW_ROUND_NOT_FOUND")
    @GetMapping
    public ResponseEntity<SuccessResponse<List<ReviewRecordRes>>> getReviews(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long roundId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                reviewRecordAdminService.getReviews(
                        principal.getId(),
                        publicId,
                        roundId)));
    }
}
