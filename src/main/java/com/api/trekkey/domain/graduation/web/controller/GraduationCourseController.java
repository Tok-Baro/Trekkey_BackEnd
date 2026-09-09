package com.api.trekkey.domain.graduation.web.controller;

import com.api.trekkey.domain.graduation.repository.StudentCourseRecordRepository;
import com.api.trekkey.domain.graduation.repository.AcademicUnitRepository;
import com.api.trekkey.domain.graduation.exception.GraduationErrorResponseCode;
import com.api.trekkey.domain.graduation.web.dto.GraduationCoursePatchReq;
import com.api.trekkey.domain.graduation.web.dto.StudentCourseRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTICIPANT')")
public class GraduationCourseController {
    private final StudentCourseRecordRepository courseRepository;
    private final AcademicUnitRepository academicUnitRepository;

    @GetMapping("/api/me/graduation/courses")
    @Transactional(readOnly = true)
    public ResponseEntity<SuccessResponse<List<StudentCourseRes>>> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(SuccessResponse.ok(courseRepository
                .findAllByProfileUserIdOrderByTermAscCourseNameAsc(principal.getId())
                .stream().map(StudentCourseRes::from).toList()));
    }

    @PatchMapping("/api/me/graduation/courses/{publicId}")
    @Transactional
    public ResponseEntity<SuccessResponse<StudentCourseRes>> patch(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String publicId,
            @RequestBody @Valid GraduationCoursePatchReq request) {
        var course = courseRepository.findByPublicIdAndProfileUserId(publicId, principal.getId())
                .orElseThrow(() -> new CustomException(GraduationErrorResponseCode.GRADUATION_COURSE_NOT_FOUND));
        var unit = request.academicUnitPublicId() == null || request.academicUnitPublicId().isBlank() ? null
                : academicUnitRepository.findByPublicIdAndOrganizationId(
                        request.academicUnitPublicId(), course.getProfile().getUser().getOrganization().getId())
                    .orElseThrow(() -> new CustomException(GraduationErrorResponseCode.GRADUATION_ACADEMIC_UNIT_NOT_FOUND));
        course.correctMapping(request.category(), unit);
        return ResponseEntity.ok(SuccessResponse.ok(StudentCourseRes.from(courseRepository.saveAndFlush(course))));
    }
}
