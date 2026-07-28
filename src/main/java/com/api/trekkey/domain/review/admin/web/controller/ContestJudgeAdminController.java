package com.api.trekkey.domain.review.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.admin.service.ContestJudgeAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ContestJudgeCreateReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewLinkIssueReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ContestJudgeRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewJudgeProgressRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewLinkIssueRes;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.response.code.SuccessResponseCode;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/contests/{publicId}/judges")
@PreAuthorize("hasRole('ADMIN')")
public class ContestJudgeAdminController {

    private final ContestJudgeAdminService contestJudgeAdminService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = {"CONTEST_JUDGE_DUPLICATED", "CONTEST_JUDGE_USER_INVALID"})
    @PostMapping
    public ResponseEntity<SuccessResponse<ContestJudgeRes>> createJudge(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @RequestBody @Valid ContestJudgeCreateReq req) {
        ContestJudgeRes response =
                contestJudgeAdminService.createJudge(principal.getId(), publicId, req);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.of(
                        response,
                        SuccessResponseCode.SUCCESS_CREATED,
                        "심사위원을 등록했습니다."
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
    public ResponseEntity<SuccessResponse<List<ContestJudgeRes>>> getJudges(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId) {
        List<ContestJudgeRes> response =
                contestJudgeAdminService.getJudges(principal.getId(), publicId);

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
            codes = {"REVIEW_ROUND_NOT_FOUND"})
    @GetMapping("/progress")
    public ResponseEntity<SuccessResponse<List<ReviewJudgeProgressRes>>>
            getJudgeProgress(
                    @AuthenticationPrincipal AuthPrincipal principal,
                    @PathVariable String publicId,
                    @RequestParam(required = false) Long roundId) {
        List<ReviewJudgeProgressRes> response =
                contestJudgeAdminService.getJudgeProgress(
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
    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = {
                    "CONTEST_JUDGE_NOT_FOUND",
                    "CONTEST_JUDGE_HAS_ASSIGNMENTS"
            })
    @DeleteMapping("/{judgeId}")
    public ResponseEntity<SuccessResponse<?>> deleteJudge(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long judgeId) {
        contestJudgeAdminService.deleteJudge(
                principal.getId(),
                publicId,
                judgeId
        );

        return ResponseEntity.ok(
                SuccessResponse.emptyCustom("심사위원을 삭제했습니다."));
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
            codes = {"CONTEST_JUDGE_NOT_FOUND", "REVIEW_LINK_EXPIRATION_INVALID"})
    @PostMapping("/{judgeId}/review-link")
    public ResponseEntity<SuccessResponse<ReviewLinkIssueRes>> issueReviewLink(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long judgeId,
            @RequestBody @Valid ReviewLinkIssueReq req) {
        ReviewLinkIssueRes response = contestJudgeAdminService.issueReviewLink(
                principal.getId(),
                publicId,
                judgeId,
                req
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(SuccessResponse.of(
                        response,
                        SuccessResponseCode.SUCCESS_CREATED,
                        "심사 링크를 발급했습니다."
                ));
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
            codes = {"CONTEST_JUDGE_NOT_FOUND"})
    @DeleteMapping("/{judgeId}/review-link")
    public ResponseEntity<SuccessResponse<?>> revokeReviewLink(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @PathVariable Long judgeId) {
        contestJudgeAdminService.revokeReviewLink(principal.getId(), publicId, judgeId);

        return ResponseEntity.ok(SuccessResponse.emptyCustom("심사 링크를 폐기했습니다."));
    }
}
