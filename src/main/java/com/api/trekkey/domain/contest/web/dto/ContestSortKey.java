package com.api.trekkey.domain.contest.web.dto;

// 관리자 목록 정렬 whitelist — 요청 정렬값은 이 enum으로 검증한 뒤 QEntity 필드에 매핑한다 (PathBuilder 금지)
public enum ContestSortKey {
    TITLE,
    DEPARTMENT,
    STATUS,
    CREATED_AT
}
