package com.api.trekkey.domain.award.admin.web.controller;

import com.api.trekkey.domain.award.admin.service.AwardAdminService;
import com.api.trekkey.domain.award.admin.web.dto.AwardCandidateUpdateReq;
import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import com.api.trekkey.domain.award.exception.AwardErrorResponseCode;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AwardAdminController {

    private final AwardAdminService awardAdminService;

    @ApiErrorCodeExamples(value = AwardErrorResponseCode.class, codes = {
            "AWARD_FINAL_ROUND_REQUIRED", "AWARD_ROUND_NOT_FINALIZED",
            "AWARD_NO_PASSED_ENTRY", "AWARD_ALREADY_CONFIRMED"})
    @PostMapping("/api/admin/review-rounds/{roundId}/awards")
    public ResponseEntity<SuccessResponse<List<AwardRes>>> calculateAwards(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long roundId) {
        List<AwardRes> response = awardAdminService.calculateAwards(authPrincipal.getId(), roundId);

        return ResponseEntity.ok(SuccessResponse.okCustom(response,
                response.size() + "건의 수상 후보를 확정 순위 기준으로 산출했습니다."));
    }

    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @GetMapping("/api/admin/contests/{contestPublicId}/awards")
    public ResponseEntity<SuccessResponse<List<AwardRes>>> getAwards(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId) {
        return ResponseEntity.ok(SuccessResponse.ok(
                awardAdminService.getAwards(authPrincipal.getId(), contestPublicId)));
    }

    @ApiErrorCodeExamples(value = AwardErrorResponseCode.class, codes = {
            "AWARD_NOT_FOUND",
            "AWARD_CANDIDATE_UPDATE_NOT_ALLOWED",
            "AWARD_CUSTOM_PRIZE_REQUIRED",
            "AWARD_CANDIDATE_STATUS_INVALID"
    })
    @PatchMapping("/api/admin/awards/{awardPublicId}")
    public ResponseEntity<SuccessResponse<AwardRes>> updateCandidate(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String awardPublicId,
            @RequestBody @Valid AwardCandidateUpdateReq request) {
        return ResponseEntity.ok(SuccessResponse.okCustom(
                awardAdminService.updateCandidate(
                        authPrincipal.getId(), awardPublicId, request),
                "수상 후보를 변경했습니다."));
    }

    @ApiErrorCodeExamples(value = AwardErrorResponseCode.class, codes = {
            "AWARD_FINAL_ROUND_REQUIRED",
            "AWARD_ROUND_NOT_FINALIZED",
            "AWARD_NO_CANDIDATE",
            "AWARD_HELD_EXISTS",
            "AWARD_CANDIDATES_STALE"
    })
    @PostMapping("/api/admin/contests/{contestPublicId}/awards/confirm")
    public ResponseEntity<SuccessResponse<List<AwardRes>>> confirmAwards(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId) {
        List<AwardRes> response = awardAdminService.confirmAwards(authPrincipal.getId(), contestPublicId);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "수상 결과를 확정했습니다."));
    }
}
