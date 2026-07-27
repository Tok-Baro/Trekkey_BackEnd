package com.api.trekkey.domain.review.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewRoundEntryAdminService;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundEntryRes;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(
        "/api/admin/contests/{publicId}/review-stages/{stageId}/entries")
public class ReviewRoundEntryAdminController {

    private final ReviewRoundEntryAdminService reviewRoundEntryAdminService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {
            "CONTEST_NOT_FOUND",
            "CONTEST_FORBIDDEN",
            "STAGE_NOT_FOUND",
            "STAGE_CONFIGURATION_INVALID",
            "REVIEW_CRITERION_REQUIRED"
    })
    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_STAGE_INVALID",
            "REVIEW_ENTRY_PREPARATION_NOT_ALLOWED",
            "REVIEW_ENTRY_CONTEST_NOT_REVIEWING",
            "REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED",
            "REVIEW_ENTRY_SUBMISSION_STAGE_NOT_COMPLETED",
            "REVIEW_ENTRY_SUBMISSION_REQUIRED",
            "REVIEW_ENTRY_SUBMISSION_INVALID",
            "REVIEW_ENTRY_DUPLICATED"
    })
    @PostMapping("/prepare")
    public ResponseEntity<SuccessResponse<List<ReviewRoundEntryRes>>>
            prepareEntries(
                    @AuthenticationPrincipal AuthPrincipal principal,
                    @PathVariable String publicId,
                    @PathVariable Long stageId) {
        List<ReviewRoundEntryRes> response =
                reviewRoundEntryAdminService.prepareEntries(
                        principal.getId(),
                        publicId,
                        stageId
                );

        return ResponseEntity.ok(SuccessResponse.okCustom(
                response,
                "심사 대상을 준비했습니다."
        ));
    }

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {
            "CONTEST_NOT_FOUND",
            "CONTEST_FORBIDDEN",
            "STAGE_NOT_FOUND"
    })
    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = "REVIEW_STAGE_INVALID")
    @GetMapping
    public ResponseEntity<SuccessResponse<List<ReviewRoundEntryRes>>>
            getEntries(
                    @AuthenticationPrincipal AuthPrincipal principal,
                    @PathVariable String publicId,
                    @PathVariable Long stageId) {
        List<ReviewRoundEntryRes> response =
                reviewRoundEntryAdminService.getEntries(
                        principal.getId(),
                        publicId,
                        stageId
                );

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
