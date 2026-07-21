package com.api.trekkey.domain.contest.publicapi.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.publicapi.service.ContestService;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contests")
public class ContestController {

    private final ContestService contestService;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<ContestSearchRes>>> searchContests(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "OPEN") ContestSearchStatus status){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(contestService.searchContests(authPrincipal.getId(), keyword, status)));
    }

    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND"})
    @GetMapping("/{publicId}")
    public ResponseEntity<SuccessResponse<ContestDetailRes>> getContestDetail(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable String publicId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(contestService.getContestDetail(authPrincipal.getId(), publicId)));
    }
}
