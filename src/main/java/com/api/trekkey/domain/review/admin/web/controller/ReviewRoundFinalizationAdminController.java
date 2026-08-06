package com.api.trekkey.domain.review.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.review.admin.service.ReviewRoundFinalizationAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundFinalizeReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundFinalizeRes;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/contests/{publicId}/review-rounds/{roundId}")
public class ReviewRoundFinalizationAdminController {

    private final ReviewRoundFinalizationAdminService finalizationService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_ROUND_NOT_FOUND",
            "REVIEW_ROUND_FINALIZATION_NOT_ALLOWED",
            "REVIEW_ROUND_ASSIGNMENTS_INCOMPLETE",
            "REVIEW_ROUND_RESULT_INVALID",
            "REVIEW_MANUAL_DECISION_INVALID"
    })
    @PostMapping("/finalize")
    public ResponseEntity<SuccessResponse<ReviewRoundFinalizeRes>> finalizeRound(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long roundId,
            @RequestBody(required = false)
            @Valid ReviewRoundFinalizeReq req
    ) {
        ReviewRoundFinalizeRes response = finalizationService.finalizeRound(
                principal.getId(),
                publicId,
                roundId,
                req
        );
        return ResponseEntity.ok(SuccessResponse.okCustom(
                response,
                "심사 라운드 결과를 확정했습니다."
        ));
    }
}
