package com.api.trekkey.domain.graduation.web.controller;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.AcademicUnitStatus;
import com.api.trekkey.domain.graduation.repository.AcademicUnitRepository;
import com.api.trekkey.domain.graduation.web.dto.AcademicUnitRes;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class GraduationAcademicUnitController {
    private final UserRepository userRepository;
    private final AcademicUnitRepository academicUnitRepository;

    @GetMapping("/api/me/graduation/academic-units")
    public ResponseEntity<SuccessResponse<List<AcademicUnitRes>>> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        Long organizationId = userRepository.findById(principal.getId())
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND))
                .getOrganization().getId();
        List<AcademicUnitRes> result = academicUnitRepository
                .findAllByOrganizationIdAndStatusOrderByName(organizationId, AcademicUnitStatus.ACTIVE)
                .stream().map(AcademicUnitRes::from).toList();
        return ResponseEntity.ok(SuccessResponse.ok(result));
    }
}
