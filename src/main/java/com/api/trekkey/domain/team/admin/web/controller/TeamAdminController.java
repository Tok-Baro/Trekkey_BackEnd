package com.api.trekkey.domain.team.admin.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.team.admin.service.TeamAdminService;
import com.api.trekkey.domain.team.admin.web.dto.TeamAdminListRes;
import com.api.trekkey.domain.team.admin.web.dto.TeamStatusUpdateReq;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TeamAdminController {

    private final TeamAdminService teamAdminService;

    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND", "CONTEST_FORBIDDEN"})
    @GetMapping("/api/admin/contests/{contestPublicId}/teams")
    public ResponseEntity<SuccessResponse<TeamAdminListRes>> getTeams(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String contestPublicId,
            @RequestParam(required = false) TeamStatus status) {
        return ResponseEntity.ok(SuccessResponse.ok(
                teamAdminService.getTeams(authPrincipal.getId(), contestPublicId, status)));
    }

    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {"TEAM_NOT_FOUND"})
    @PatchMapping("/api/admin/teams/{teamPublicId}/status")
    public ResponseEntity<SuccessResponse<TeamRes>> changeStatus(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String teamPublicId,
            @RequestBody @Valid TeamStatusUpdateReq request) {
        TeamRes response = teamAdminService.changeStatus(authPrincipal.getId(), teamPublicId, request);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "신청 상태를 변경했습니다."));
    }

    @ApiErrorCodeExamples(value = TeamErrorResponseCode.class, codes = {
            "TEAM_NOT_FOUND",
            "TEAM_ALREADY_FINALIZED",
            "TEAM_NOT_APPROVED"
    })
    @PostMapping("/api/admin/teams/{teamPublicId}/finalize")
    public ResponseEntity<SuccessResponse<TeamRes>> finalizeParticipation(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String teamPublicId) {
        TeamRes response = teamAdminService.finalizeParticipation(authPrincipal.getId(), teamPublicId);

        return ResponseEntity.ok(SuccessResponse.okCustom(response, "팀원 명단을 확정했습니다."));
    }
}
