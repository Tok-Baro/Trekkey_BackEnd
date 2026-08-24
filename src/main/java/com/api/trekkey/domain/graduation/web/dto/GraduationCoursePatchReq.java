package com.api.trekkey.domain.graduation.web.dto;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.CourseCategory;
import jakarta.validation.constraints.NotNull;

public record GraduationCoursePatchReq(@NotNull CourseCategory category, String academicUnitPublicId) { }
