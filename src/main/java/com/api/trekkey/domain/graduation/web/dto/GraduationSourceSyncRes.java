package com.api.trekkey.domain.graduation.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GraduationSourceSyncRes(LocalDateTime syncedAt, List<Source> sources) {
    public record Source(String policyCode, String title, String url, String status, String contentHash, String message) { }
}
