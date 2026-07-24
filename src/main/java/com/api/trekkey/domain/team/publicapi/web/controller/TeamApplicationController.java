package com.api.trekkey.domain.team.publicapi.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contests/{publicId}/applications")
public class TeamApplicationController {

    private final TeamApplicationService teamApplicationService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {"USER_NOT_FOUND"})
    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND"})
    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {
            "TEAM_APPLICATION_NOT_OPEN",
            "TEAM_APPLICATION_ALREADY_EXISTS",
            "TEAM_APPLICATION_MEMBER_COUNT_INVALID"
    })
    @PostMapping
    public ResponseEntity<SuccessResponse<?>> createApplication(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId,
            @RequestBody @Valid TeamApplicationCreateReq request) {
        teamApplicationService.createApplication(authPrincipal.getId(), publicId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.createSuccess("참가 신청을 접수했습니다."));
    }
}
