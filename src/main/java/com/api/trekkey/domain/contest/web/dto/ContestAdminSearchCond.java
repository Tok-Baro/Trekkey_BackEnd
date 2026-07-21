package com.api.trekkey.domain.contest.web.dto;

import com.api.trekkey.domain.contest.entity.ContestStatus;

public record ContestAdminSearchCond(
        ContestStatus status,
        String keyword,
        ContestSortKey sortKey,
        String sortDir,
        Integer page,
        Integer size
) {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 1000;

    public ContestAdminSearchCond {
        sortKey = sortKey == null ? ContestSortKey.CREATED_AT : sortKey;
        sortDir = sortDir == null || sortDir.isBlank() ? "DESC" : sortDir;
        page = page == null || page < 0 ? 0 : page;
        size = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
