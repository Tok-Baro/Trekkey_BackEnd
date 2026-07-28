package com.api.trekkey.domain.review.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewRoundAdminService;
import com.api.trekkey.domain.review.web.dto.request.ReviewRoundSaveReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundRes;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.response.code.SuccessResponseCode;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/contests/{publicId}/review-rounds")
public class ReviewRoundAdminController {

    private final ReviewRoundAdminService reviewRoundAdminService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_ROUND_DUPLICATED",
            "REVIEW_ROUND_CONFIGURATION_INVALID",
            "REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED",
            "REVIEW_ROUND_CRITERION_INVALID",
            "REVIEW_ROUND_CRITERION_DUPLICATED"
    })
    @PostMapping
    public ResponseEntity<SuccessResponse<ReviewRoundRes>> createRound(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @RequestBody @Valid ReviewRoundSaveReq req
    ) {
        ReviewRoundRes response = reviewRoundAdminService.createRound(
                principal.getId(),
                publicId,
                req
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.of(
                        response,
                        SuccessResponseCode.SUCCESS_CREATED,
                        "심사 라운드를 생성했습니다."
                ));
    }

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @GetMapping
    public ResponseEntity<SuccessResponse<List<ReviewRoundRes>>> getRounds(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId
    ) {
        List<ReviewRoundRes> response = reviewRoundAdminService.getRounds(
                principal.getId(),
                publicId
        );

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

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
    @GetMapping("/{roundId}")
    public ResponseEntity<SuccessResponse<ReviewRoundRes>> getRound(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long roundId
    ) {
        ReviewRoundRes response = reviewRoundAdminService.getRound(
                principal.getId(),
                publicId,
                roundId
        );

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_ROUND_NOT_FOUND",
            "REVIEW_ROUND_DUPLICATED",
            "REVIEW_ROUND_CONFIGURATION_LOCKED",
            "REVIEW_ROUND_CONFIGURATION_INVALID",
            "REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED",
            "REVIEW_ROUND_CRITERION_NOT_FOUND",
            "REVIEW_ROUND_CRITERION_INVALID",
            "REVIEW_ROUND_CRITERION_CODE_IMMUTABLE",
            "REVIEW_ROUND_CRITERION_DUPLICATED"
    })
    @PutMapping("/{roundId}")
    public ResponseEntity<SuccessResponse<ReviewRoundRes>> updateRound(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long roundId,
            @RequestBody @Valid ReviewRoundSaveReq req
    ) {
        ReviewRoundRes response = reviewRoundAdminService.updateRound(
                principal.getId(),
                publicId,
                roundId,
                req
        );

        return ResponseEntity.ok(SuccessResponse.okCustom(
                response,
                "심사 라운드 설정을 저장했습니다."
        ));
    }

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "REVIEW_ROUND_NOT_FOUND",
            "REVIEW_ROUND_STATUS_TRANSITION_INVALID",
            "REVIEW_ROUND_OPEN_WINDOW_EXPIRED",
            "REVIEW_ROUND_CONFIGURATION_INVALID",
            "REVIEW_ROUND_CRITERION_REQUIRED",
            "REVIEW_ENTRY_REQUIRED",
            "REVIEW_ROUND_ENTRY_INVALID"
    })
    @PostMapping("/{roundId}/open")
    public ResponseEntity<SuccessResponse<ReviewRoundRes>> openRound(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long roundId
    ) {
        ReviewRoundRes response = reviewRoundAdminService.openRound(
                principal.getId(),
                publicId,
                roundId
        );

        return ResponseEntity.ok(SuccessResponse.okCustom(
                response,
                "심사 라운드를 시작했습니다."
        ));
    }
}
