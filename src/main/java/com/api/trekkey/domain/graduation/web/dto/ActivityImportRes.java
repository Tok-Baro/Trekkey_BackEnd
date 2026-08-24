package com.api.trekkey.domain.graduation.web.dto;

import java.util.List;

public record ActivityImportRes(
        boolean applied,
        String sourceType,
        int detectedPoints,
        int activityCount,
        List<String> warnings,
        List<Activity> activities) {
    public record Activity(String title, int points, String completedAt) { }
}
