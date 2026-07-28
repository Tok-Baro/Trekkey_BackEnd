package com.api.trekkey.domain.contest.publicapi.web.dto;

public record ContestLikeRes(
        long likeCount,
        boolean likedByMe
) {
}
