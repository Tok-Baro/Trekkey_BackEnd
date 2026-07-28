package com.api.trekkey.domain.team.publicapi.web.controller;

import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantTeamRes;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/teams")
@PreAuthorize("hasRole('PARTICIPANT')")
public class ParticipantTeamController {

    private final TeamApplicationService teamApplicationService;

    @ApiErrorCodeExamples(value = UserErrorResponseCode.class, codes = {"USER_NOT_FOUND"})
    @GetMapping
    public ResponseEntity<SuccessResponse<List<ParticipantTeamRes>>> getMyTeams(
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        return ResponseEntity.ok(
                SuccessResponse.ok(teamApplicationService.getMyTeams(authPrincipal.getId())));
    }
}
