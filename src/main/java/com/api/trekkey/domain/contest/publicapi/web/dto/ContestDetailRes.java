package com.api.trekkey.domain.contest.publicapi.web.dto;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageType;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record ContestDetailRes(
        String publicId,
        String title,
        ContestStatus status,
        ParticipationType participationType,
        String posterUrl,
        String summary,
        List<String> tags,
        String department,
        LocalDateTime applicationStartsAt,
        LocalDateTime applicationEndsAt,
        LocalDateTime submissionDueAt,
        String target,
        int awardCount,
        String applicationMethod,
        String benefits,
        String detailHtml,
        long viewCount,
        long likeCount
) {
    public static ContestDetailRes from(
            Contest contest,
            List<ContestStage> stages,
            long likeCount) {
        ContestStage applicationStage = findStage(stages, StageType.APPLICATION);
        ContestStage submissionStage = findStage(stages, StageType.SUBMISSION);

        return new ContestDetailRes(
                contest.getPublicId(),
                contest.getTitle(),
                contest.getStatus(),
                contest.getParticipationType(),
                contest.getPosterUrl(),
                contest.getSummary(),
                toTags(contest.getTags()),
                contest.getDepartment(),
                applicationStage == null ? null : applicationStage.getStartsAt(),
                applicationStage == null ? null : applicationStage.getEndsAt(),
                submissionStage == null ? null : submissionStage.getEndsAt(),
                contest.getTarget(),
                contest.getAwardCount(),
                contest.getApplicationMethod(),
                contest.getBenefits(),
                contest.getDetailHtml(),
                contest.getViewCount(),
                likeCount);
    }

    private static ContestStage findStage(List<ContestStage> stages, StageType stageType) {
        return stages.stream()
                .filter(stage -> stage.getStageType() == stageType)
                .findFirst()
                .orElse(null);
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
