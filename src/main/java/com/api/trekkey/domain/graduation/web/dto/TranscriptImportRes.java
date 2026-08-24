package com.api.trekkey.domain.graduation.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record TranscriptImportRes(
        boolean applied,
        String sourceType,
        int courseCount,
        BigDecimal detectedTotalCredits,
        BigDecimal detectedGpa,
        String latestTerm,
        List<String> warnings,
        List<Course> courses) {

    public record Course(
            String term,
            String courseCode,
            String courseName,
            BigDecimal credits,
            String grade,
            String category,
            String completionStatus) {
    }
}
