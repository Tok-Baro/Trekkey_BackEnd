package com.api.trekkey.domain.contest.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.admin.service.ContestAdminQueryService;
import com.api.trekkey.domain.contest.admin.service.ContestCommandService;
import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSummaryRes;
import com.api.trekkey.domain.contest.admin.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.admin.web.dto.StageStatusUpdateReq;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.PageRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.response.code.SuccessResponseCode;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ContestAdminController {

    private final ContestCommandService contestCommandService;
    private final ContestAdminQueryService contestAdminQueryService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {
                    "INVALID_STAGE_STATUS_TRANSITION"
            })
    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = "REVIEW_ROUND_REQUIRED")
    @PostMapping("/api/contests")
    public ResponseEntity<SuccessResponse<ContestDetailRes>> createContest(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody @Valid ContestCreateReq contestCreateReq) {
        ContestDetailRes response = contestCommandService.createContest(principal.getId(), contestCreateReq);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.of(response, SuccessResponseCode.SUCCESS_CREATED, "새 대회를 생성했습니다."));
    }

    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {
                    "CONTEST_NOT_FOUND",
                    "CONTEST_FORBIDDEN",
                    "STAGE_NOT_FOUND",
                    "STAGE_DUPLICATED",
                    "STAGE_CONFIGURATION_LOCKED",
                    "INVALID_STAGE_STATUS_TRANSITION"
            })
    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = "REVIEW_ROUND_REQUIRED")
    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @PutMapping("/api/contests/{publicId}")
    public ResponseEntity<SuccessResponse<ContestDetailRes>> updateContest(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @RequestBody @Valid ContestCreateReq contestCreateReq) {
        ContestDetailRes response =
                contestCommandService.updateContest(principal.getId(), publicId, contestCreateReq);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "대회 설정을 저장했습니다."));
    }

    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = {
                    "STAGE_NOT_FOUND",
                    "CONTEST_FORBIDDEN",
                    "INVALID_STAGE_STATUS_TRANSITION",
                    "STAGE_CONFIGURATION_INVALID"
            })
    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ReviewErrorResponseCode.class,
            codes = "REVIEW_ROUND_REQUIRED")
    @PatchMapping("/api/stages/{stageId}/status")
    public ResponseEntity<SuccessResponse<StageRes>> updateStageStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long stageId,
            @RequestBody @Valid StageStatusUpdateReq stageStatusUpdateReq) {
        StageRes response =
                contestCommandService.updateStageStatus(principal.getId(), stageId, stageStatusUpdateReq);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "단계 상태를 변경했습니다."));
    }

    // 관리자 콘솔 대회 목록 — 상태/키워드 필터, 정렬 whitelist, 페이징
    @GetMapping("/api/admin/contests")
    public ResponseEntity<SuccessResponse<PageRes<ContestAdminSummaryRes>>> getAdminContests(
            @AuthenticationPrincipal AuthPrincipal principal,
            @ModelAttribute ContestAdminSearchCond cond) {
        PageRes<ContestAdminSummaryRes> response =
                contestAdminQueryService.getContests(principal.getId(), cond);

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
