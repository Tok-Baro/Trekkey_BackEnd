package com.api.trekkey.domain.graduation.web.dto;

import com.api.trekkey.domain.graduation.entity.StudentCourseRecord;
import java.math.BigDecimal;

public record StudentCourseRes(
        String publicId,
        String term,
        String courseCode,
        String courseName,
        BigDecimal credits,
        String grade,
        String category,
        String completionStatus,
        String mappingStatus,
        String sourceType,
        String academicUnitPublicId,
        String academicUnitName) {
    public static StudentCourseRes from(StudentCourseRecord course) {
        return new StudentCourseRes(course.getPublicId(), course.getTerm(), course.getCourseCode(),
                course.getCourseName(), course.getCredits(), course.getGrade(), course.getCategory().name(),
                course.getCompletionStatus().name(), course.getMappingStatus().name(), course.getSourceType().name(),
                course.getAcademicUnit() == null ? null : course.getAcademicUnit().getPublicId(),
                course.getAcademicUnit() == null ? null : course.getAcademicUnit().getName());
    }
}
