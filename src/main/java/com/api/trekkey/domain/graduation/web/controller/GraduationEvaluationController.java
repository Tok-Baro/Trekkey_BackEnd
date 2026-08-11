package com.api.trekkey.domain.graduation.web.controller;

import com.api.trekkey.domain.graduation.service.GraduationEvaluationService;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationReq;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class GraduationEvaluationController {
    private final GraduationEvaluationService graduationEvaluationService;

    @PostMapping("/api/me/graduation/evaluations")
    public ResponseEntity<SuccessResponse<GraduationEvaluationRes>> evaluate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody(required = false) GraduationEvaluationReq request) {
        LocalDate policyAsOf = request == null ? null : request.policyAsOf();
        GraduationEvaluationRes response = graduationEvaluationService.evaluate(principal.getId(), policyAsOf);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.create(response));
    }
}
