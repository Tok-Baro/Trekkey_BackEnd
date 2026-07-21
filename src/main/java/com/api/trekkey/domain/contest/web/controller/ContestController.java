package com.api.trekkey.domain.contest.web.controller;

import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.service.ContestService;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.web.dto.ContestSearchStatus;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.swagger.ApiErrorCodeExamples;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "OPEN") ContestSearchStatus status) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(SuccessResponse.ok(
                contestService.searchContests(principal.getId(), keyword, status)));
    }

    // 공개 공고 상세 — 비로그인 접근 허용 (SecurityConfig: GET /api/contests/* permitAll)
    @ApiErrorCodeExamples(value = ContestErrorResponseCode.class, codes = {"CONTEST_NOT_FOUND"})
    @GetMapping("/{publicId}")
    public ResponseEntity<SuccessResponse<ContestDetailRes>> getContestDetail(
            @PathVariable String publicId) {
        return ResponseEntity.ok(SuccessResponse.ok(contestService.getContestDetail(publicId)));
    }
}
