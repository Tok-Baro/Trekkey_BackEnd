package com.api.trekkey.domain.review.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.review.admin.service.ReviewAdminService;
import com.api.trekkey.domain.review.admin.web.dto.EntryDecisionReq;
import com.api.trekkey.domain.review.admin.web.dto.EntryRes;
import com.api.trekkey.domain.review.admin.web.dto.JudgeCreateReq;
import com.api.trekkey.domain.review.admin.web.dto.JudgeRes;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReviewAdminController {

    private final ReviewAdminService reviewAdminService;

    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @PostMapping("/api/admin/contests/{contestPublicId}/judges")
    public ResponseEntity<SuccessResponse<JudgeRes>> createJudge(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId,
            @RequestBody @Valid JudgeCreateReq request) {
        JudgeRes response = reviewAdminService.createJudge(authPrincipal.getId(), contestPublicId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.of(response,
                        com.api.trekkey.global.response.code.SuccessResponseCode.SUCCESS_CREATED,
                        "심사위원을 등록하고 심사 링크를 발급했습니다."));
    }

    @GetMapping("/api/admin/contests/{contestPublicId}/judges")
    public ResponseEntity<SuccessResponse<List<JudgeRes>>> getJudges(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                reviewAdminService.getJudges(authPrincipal.getId(), contestPublicId)));
    }

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {"JUDGE_NOT_FOUND"})
    @PostMapping("/api/admin/judges/{judgeId}/token")
    public ResponseEntity<SuccessResponse<JudgeRes>> rotateToken(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long judgeId) {
        JudgeRes response = reviewAdminService.rotateToken(authPrincipal.getId(), judgeId);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "심사 링크를 재발급했습니다."));
    }

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {"JUDGE_NOT_FOUND", "JUDGE_HAS_ASSIGNMENTS"})
    @DeleteMapping("/api/admin/judges/{judgeId}")
    public ResponseEntity<SuccessResponse<?>> deleteJudge(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long judgeId) {
        reviewAdminService.deleteJudge(authPrincipal.getId(), judgeId);

        return ResponseEntity.ok(SuccessResponse.emptyCustom("심사위원을 삭제했습니다."));
    }

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {
            "ROUND_NOT_REVIEW_STAGE", "ROUND_ALREADY_OPENED", "ROUND_NO_TARGET", "ROUND_NO_JUDGE"})
    @PostMapping("/api/admin/stages/{stageId}/open")
    public ResponseEntity<SuccessResponse<List<EntryRes>>> openRound(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long stageId) {
        List<EntryRes> response = reviewAdminService.openRound(authPrincipal.getId(), stageId);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "라운드를 시작하고 심사를 배정했습니다."));
    }

    @GetMapping("/api/admin/stages/{stageId}/entries")
    public ResponseEntity<SuccessResponse<List<EntryRes>>> getEntries(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long stageId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                reviewAdminService.getEntries(authPrincipal.getId(), stageId)));
    }

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {"ROUND_NOT_OPEN", "ROUND_NO_TARGET"})
    @PostMapping("/api/admin/stages/{stageId}/finalize")
    public ResponseEntity<SuccessResponse<List<EntryRes>>> finalizeRound(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long stageId) {
        List<EntryRes> response = reviewAdminService.finalizeRound(authPrincipal.getId(), stageId);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "라운드 결과를 확정했습니다."));
    }

    @ApiErrorCodeExamples(value = ReviewErrorResponseCode.class, codes = {"ENTRY_NOT_FOUND", "ENTRY_ALREADY_FINALIZED"})
    @PatchMapping("/api/admin/entries/{entryId}")
    public ResponseEntity<SuccessResponse<EntryRes>> decideEntry(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long entryId,
            @RequestBody @Valid EntryDecisionReq request) {
        EntryRes response = reviewAdminService.decideEntry(authPrincipal.getId(), entryId, request);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "심사 대상을 수동 판정했습니다."));
    }
}
