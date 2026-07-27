package com.api.trekkey.domain.award.publicapi.web.controller;

import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.award.repository.AwardRepository;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class MyAwardController {

    private final AwardRepository awardRepository;

    // 내 수상 목록 — 확정된 수상만 (참가자 포털 결과 화면)
    @GetMapping("/api/users/me/awards")
    @Transactional(readOnly = true)
    public ResponseEntity<SuccessResponse<List<AwardRes>>> getMyAwards(
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        List<AwardRes> response = awardRepository
                .findAllVisibleToUserByStatusOrderByConfirmedAtDesc(
                        authPrincipal.getId(), AwardStatus.CONFIRMED).stream()
                .map(AwardRes::from)
                .toList();

        return ResponseEntity.ok(SuccessResponse.ok(response));
    }
}
