package com.api.trekkey.domain.team.publicapi.web.controller;

import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamRes;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class TeamController {

    private final TeamApplicationService teamApplicationService;

    // 내 참가 신청 목록 — 참가자 포털 내 신청 화면
    @GetMapping("/api/users/me/applications")
    public ResponseEntity<SuccessResponse<List<TeamRes>>> getMyApplications(
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ResponseEntity.ok(SuccessResponse.ok(
                teamApplicationService.getMyApplications(authPrincipal.getId())));
    }

    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {
            "TEAM_NOT_FOUND",
            "TEAM_FORBIDDEN",
            "TEAM_ALREADY_FINALIZED",
            "TEAM_APPLICATION_MEMBER_COUNT_INVALID"
    })
    @PatchMapping("/api/teams/{teamPublicId}")
    public ResponseEntity<SuccessResponse<TeamRes>> updateApplication(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String teamPublicId,
            @RequestBody @Valid TeamApplicationUpdateReq request) {
        TeamRes response =
                teamApplicationService.updateApplication(authPrincipal.getId(), teamPublicId, request);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "신청 정보를 수정했습니다."));
    }
}
