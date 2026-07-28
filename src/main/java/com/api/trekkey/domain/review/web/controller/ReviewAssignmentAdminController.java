package com.api.trekkey.domain.review.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewAssignmentAdminService;
import com.api.trekkey.domain.review.web.dto.request.ReviewAssignmentPrepareReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAssignmentRes;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(
        "/api/admin/contests/{publicId}/review-rounds/{roundId}"
                + "/judges/{judgeId}/assignments")
public class ReviewAssignmentAdminController {

    private final ReviewAssignmentAdminService reviewAssignmentAdminService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {
            "CONTEST_NOT_FOUND",
            "CONTEST_FORBIDDEN"
    })
    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "CONTEST_JUDGE_NOT_FOUND",
            "REVIEW_ROUND_NOT_FOUND",
            "REVIEW_ASSIGNMENT_PREPARATION_NOT_ALLOWED",
            "REVIEW_ASSIGNMENT_ENTRY_REQUIRED",
            "REVIEW_ASSIGNMENT_ENTRY_INVALID",
            "REVIEW_ASSIGNMENT_DUE_AT_INVALID",
            "REVIEW_ASSIGNMENT_DUPLICATED"
    })
    @PostMapping("/prepare")
    public ResponseEntity<SuccessResponse<List<ReviewAssignmentRes>>>
            prepareAssignments(
                    @AuthenticationPrincipal AuthPrincipal principal,
                    @PathVariable String publicId,
                    @PathVariable Long roundId,
                    @PathVariable Long judgeId,
                    @RequestBody(required = false)
                    @Valid ReviewAssignmentPrepareReq req) {
        List<ReviewAssignmentRes> response =
                reviewAssignmentAdminService.prepareAssignments(
                        principal.getId(),
                        publicId,
                        roundId,
                        judgeId,
                        req
                );

        return ResponseEntity.ok(SuccessResponse.okCustom(
                response,
                "심사위원 평가표를 준비했습니다."
        ));
    }

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {
            "CONTEST_NOT_FOUND",
            "CONTEST_FORBIDDEN"
    })
    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "CONTEST_JUDGE_NOT_FOUND",
            "REVIEW_ROUND_NOT_FOUND"
    })
    @GetMapping
    public ResponseEntity<SuccessResponse<List<ReviewAssignmentRes>>>
            getAssignments(
                    @AuthenticationPrincipal AuthPrincipal principal,
                    @PathVariable String publicId,
                    @PathVariable Long roundId,
                    @PathVariable Long judgeId) {
        List<ReviewAssignmentRes> response =
                reviewAssignmentAdminService.getAssignments(
                        principal.getId(),
                        publicId,
                        roundId,
                        judgeId
                );

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
