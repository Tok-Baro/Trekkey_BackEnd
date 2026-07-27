package com.api.trekkey.domain.submission.publicapi.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.service.SubmissionService;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionSaveReq;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@PreAuthorize("hasRole('PARTICIPANT')")
@RequestMapping("/api/contests/{publicId}/submission")
public class SubmissionController {

    private final SubmissionService submissionService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = "CONTEST_NOT_FOUND")
    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_TEAM_NOT_FOUND",
            "SUBMISSION_NOT_FOUND"
    })
    @GetMapping
    public ResponseEntity<SuccessResponse<SubmissionRes>> getSubmission(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId) {
        SubmissionRes response =
                submissionService.getSubmission(authPrincipal.getId(), publicId);

        return ResponseEntity.ok(
                SuccessResponse.okCustom(response, "제출물을 조회했습니다."));
    }

    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_TEAM_NOT_FOUND",
            "SUBMISSION_TEAM_NOT_APPROVED",
            "SUBMISSION_STAGE_INVALID",
            "SUBMISSION_NOT_OPEN",
            "SUBMISSION_ALREADY_EXISTS",
            "INVALID_SUBMISSION_STATUS_TRANSITION"
    })
    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = "CONTEST_NOT_FOUND")
    @PutMapping
    public ResponseEntity<SuccessResponse<SubmissionRes>> saveDraft(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId,
            @RequestBody @Valid SubmissionSaveReq request) {
        SubmissionRes response =
                submissionService.saveDraft(authPrincipal.getId(), publicId, request);

        return ResponseEntity.ok(
                SuccessResponse.okCustom(response, "제출물 초안을 저장했습니다."));
    }

    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_NOT_FOUND",
            "SUBMISSION_TEAM_NOT_FOUND",
            "SUBMISSION_TEAM_NOT_APPROVED",
            "SUBMISSION_STAGE_INVALID",
            "SUBMISSION_NOT_OPEN",
            "INVALID_SUBMISSION_STATUS_TRANSITION"
    })
    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = "CONTEST_NOT_FOUND")
    @PostMapping("/submit")
    public ResponseEntity<SuccessResponse<SubmissionRes>> submit(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId) {
        SubmissionRes response =
                submissionService.submit(authPrincipal.getId(), publicId);

        return ResponseEntity.ok(
                SuccessResponse.okCustom(response, "제출물을 제출했습니다."));
    }

    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_NOT_FOUND",
            "SUBMISSION_TEAM_NOT_FOUND",
            "SUBMISSION_TEAM_NOT_APPROVED",
            "SUBMISSION_STAGE_INVALID",
            "SUBMISSION_NOT_OPEN",
            "INVALID_SUBMISSION_STATUS_TRANSITION"
    })
    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = "CONTEST_NOT_FOUND")
    @PostMapping("/reopen")
    public ResponseEntity<SuccessResponse<SubmissionRes>> reopen(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId) {
        SubmissionRes response =
                submissionService.reopen(authPrincipal.getId(), publicId);

        return ResponseEntity.ok(
                SuccessResponse.okCustom(
                        response,
                        "제출물 수정을 다시 시작했습니다."
                ));
    }

    @ApiErrorCodeExamples(value = SubmissionErrorResponseCode.class, codes = {
            "SUBMISSION_NOT_FOUND",
            "SUBMISSION_TEAM_NOT_FOUND",
            "SUBMISSION_TEAM_NOT_APPROVED",
            "SUBMISSION_STAGE_INVALID",
            "SUBMISSION_NOT_OPEN",
            "INVALID_SUBMISSION_STATUS_TRANSITION"
    })
    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {
            "USER_NOT_FOUND",
            "USER_INVALID_TOKEN"
    })
    @ApiErrorCodeExamples(
            value = ContestErrorResponseCode.class,
            codes = "CONTEST_NOT_FOUND")
    @PostMapping("/withdraw")
    public ResponseEntity<SuccessResponse<SubmissionRes>> withdraw(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId) {
        SubmissionRes response =
                submissionService.withdraw(authPrincipal.getId(), publicId);

        return ResponseEntity.ok(
                SuccessResponse.okCustom(response, "제출물을 철회했습니다."));
    }
}
