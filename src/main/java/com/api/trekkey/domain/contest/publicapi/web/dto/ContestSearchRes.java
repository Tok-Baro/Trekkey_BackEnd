package com.api.trekkey.domain.contest.publicapi.web.dto;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record ContestSearchRes(
        String publicId,
        String title,
        ContestStatus status,
        String posterUrl,
        String summary,
        List<String> tags,
        LocalDateTime submissionDueAt,
        long viewCount,
        long likeCount
) {
    public static ContestSearchRes from(
            Contest contest,
            LocalDateTime submissionDueAt,
            long likeCount) {
        return new ContestSearchRes(
                contest.getPublicId(),
                contest.getTitle(),
                contest.getStatus(),
                contest.getPosterUrl(),
                contest.getSummary(),
                toTags(contest.getTags()),
                submissionDueAt,
                contest.getViewCount(),
                likeCount);
    }

    private static List<String> toTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }

        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }
}
