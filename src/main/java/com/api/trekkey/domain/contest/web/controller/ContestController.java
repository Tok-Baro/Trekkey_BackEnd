package com.api.trekkey.domain.contest.web.controller;

import com.api.trekkey.domain.contest.service.ContestService;
import com.api.trekkey.domain.contest.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.web.dto.ContestSearchStatus;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
}
