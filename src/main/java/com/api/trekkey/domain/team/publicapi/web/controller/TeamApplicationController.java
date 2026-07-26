package com.api.trekkey.domain.team.publicapi.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantSearchRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@PreAuthorize("hasRole('PARTICIPANT')")
public class TeamApplicationController {

    private final TeamApplicationService teamApplicationService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {"USER_NOT_FOUND"})
    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND"})
    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {
            "TEAM_APPLICATION_NOT_OPEN",
            "TEAM_APPLICATION_ALREADY_EXISTS",
            "TEAM_APPLICATION_MEMBER_COUNT_INVALID",
            "TEAM_APPLICATION_MEMBER_INVALID",
            "TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING"
    })
    @PostMapping("/contests/{publicId}/applications")
    public ResponseEntity<SuccessResponse<?>> createApplication(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId,
            @RequestBody @Valid TeamApplicationCreateReq request) {
        teamApplicationService.createApplication(authPrincipal.getId(), publicId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.createSuccess("참가 신청을 접수했습니다."));
    }

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {"USER_NOT_FOUND"})
    @GetMapping("/me/applications")
    public ResponseEntity<SuccessResponse<List<TeamApplicationRes>>> getMyApplications(
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(teamApplicationService.getMyApplications(authPrincipal.getId())));
    }

    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {
            "TEAM_NOT_FOUND",
            "TEAM_ALREADY_FINALIZED",
            "TEAM_APPLICATION_MEMBER_COUNT_INVALID",
            "TEAM_APPLICATION_MEMBER_INVALID",
            "TEAM_APPLICATION_MEMBER_ALREADY_PARTICIPATING"
    })
    @PatchMapping("/me/applications/{contestPublicId}")
    public ResponseEntity<SuccessResponse<?>> updateApplication(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId,
            @RequestBody @Valid TeamApplicationUpdateReq request) {
        teamApplicationService.updateApplication(
                authPrincipal.getId(),
                contestPublicId,
                request);

        return ResponseEntity.ok(SuccessResponse.emptyCustom("신청 정보를 수정했습니다."));
    }

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {"USER_NOT_FOUND"})
    @GetMapping("/participants/search")
    public ResponseEntity<SuccessResponse<List<ParticipantSearchRes>>> searchParticipants(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @RequestParam String keyword) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(
                        teamApplicationService.searchParticipants(authPrincipal.getId(), keyword)));
    }
}
